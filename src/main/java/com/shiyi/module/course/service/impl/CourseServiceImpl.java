package com.shiyi.module.course.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shiyi.common.BusinessException;
import com.shiyi.infra.MinioService;
import com.shiyi.module.course.dto.CourseCreateDTO;
import com.shiyi.module.course.dto.CourseSessionCreateDTO;
import com.shiyi.module.course.entity.Course;
import com.shiyi.module.course.entity.CourseSession;

import com.shiyi.module.course.mapper.CourseMapper;
import com.shiyi.module.course.mapper.CourseSessionMapper;
import com.shiyi.module.course.service.CourseService;
import com.shiyi.module.inheritor.entity.Inheritor;
import com.shiyi.module.inheritor.service.InheritorService;
import com.shiyi.module.search.mq.CourseSyncEvent;
import com.shiyi.module.search.mq.RabbitMQConfig;
import com.shiyi.module.seckill.service.SeckillService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CourseServiceImpl implements CourseService {

    private final CourseMapper courseMapper;
    private final CourseSessionMapper courseSessionMapper;
    private final InheritorService inheritorService;
    private final MinioService minioService;
    private final StringRedisTemplate stringRedisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final SeckillService seckillService;

    /** 课程变更后的一致性动作:删详情缓存(Cache-Aside)+ 发 ES 同步事件,收口一处防漏 */
    private void notifyCourseChanged(Long courseId) {
        stringRedisTemplate.delete("cache:course:detail:" + courseId);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_COURSE, "", new CourseSyncEvent(courseId));
    }

    @Override
    public void verify(Long courseId, boolean pass, String reason) {
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new BusinessException("课程不存在");
        }
        // UpdateWrapper 显式置空:通过时清掉历史驳回原因(null 字段走 updateById 不落库)
        String cleanReason = pass ? null
                : (reason == null || reason.isBlank() ? null : reason.trim());
        courseMapper.update(null, Wrappers.<Course>lambdaUpdate()
                .eq(Course::getId, courseId)
                .set(Course::getStatus, pass ? Course.STATUS_ONLINE : Course.STATUS_OFFLINE)
                .set(Course::getRejectReason, cleanReason));
        notifyCourseChanged(courseId);
    }

    /**
     * 传承人改课:只允许 待审核/未通过 的课编辑。
     * 被驳回(有原因)的保存后自动回到待审核;手动下架的保持下架,是否重新上架由管理员决定
     */
    @Override
    public void updateCourse(Long courseId, CourseCreateDTO dto) {
        Course course = getMyCourse(courseId);   // 属主校验
        if (course.getStatus() != Course.STATUS_PENDING && course.getStatus() != Course.STATUS_OFFLINE) {
            throw new BusinessException("仅待审核或未通过的课程可以编辑");
        }
        boolean resubmit = course.getStatus() == Course.STATUS_OFFLINE && course.getRejectReason() != null;
        courseMapper.update(null, Wrappers.<Course>lambdaUpdate()
                .eq(Course::getId, courseId)
                .set(Course::getHeritageItemId, dto.getHeritageItemId())
                .set(Course::getTitle, dto.getTitle())
                .set(Course::getAddress, dto.getAddress())
                .set(Course::getLng, dto.getLng())
                .set(Course::getLat, dto.getLat())
                .set(Course::getDescription, dto.getDescription())
                .set(Course::getTags, dto.getTags())
                .set(Course::getStatus, resubmit ? Course.STATUS_PENDING : course.getStatus())
                .set(Course::getRejectReason, null));
    }

    @Override
    public void publish(CourseCreateDTO dto) {
        Inheritor inheritor = inheritorService.currentInheritor();
        Course course = new Course();
        course.setInheritorId(inheritor.getId());
        course.setHeritageItemId(dto.getHeritageItemId());
        course.setTitle(dto.getTitle());
        course.setAddress(dto.getAddress());
        course.setLng(dto.getLng());
        course.setLat(dto.getLat());
        course.setDescription(dto.getDescription());
        course.setTags(dto.getTags());
        course.setStatus(Course.STATUS_PENDING);   // 新课一律先进待审核，不直接可卖
        course.setSales(0);
        course.setDeleted(0);
        courseMapper.insert(course);
    }

    @Override
    public List<Course> listMy() {
        Inheritor inheritor = inheritorService.currentInheritor();
        return courseMapper.selectList(new LambdaQueryWrapper<Course>()
                .eq(Course::getInheritorId, inheritor.getId())
                .eq(Course::getDeleted, 0)
                .orderByDesc(Course::getCreateTime));
    }

    @Override
    public List<Course> listByStatus(Integer status) {
        return courseMapper.selectList(new LambdaQueryWrapper<Course>()
                .eq(Course::getStatus, status)
                .eq(Course::getDeleted, 0));
    }

    @Override
    public void addSession(Long courseId, CourseSessionCreateDTO dto) {
        Course course = getMyCourse(courseId);     // 属主校验入口
        CourseSession session = new CourseSession();
        session.setCourseId(course.getId());
        session.setStartTime(dto.getStartTime());
        session.setDurationMin(dto.getDurationMin());
        session.setPrice(dto.getPrice());
        session.setCapacity(dto.getCapacity());
        session.setRemainingStock(dto.getCapacity());   // 建场时剩余 = 总名额
        session.setStatus(CourseSession.STATUS_OPEN);
        courseSessionMapper.insert(session);
        seckillService.preheat(session.getId());   // 建场次即预热,Redis key = 开门信号
    }

    @Override
    public List<CourseSession> listSessions(Long courseId) {
        getMyCourse(courseId);
        return courseSessionMapper.selectList(new LambdaQueryWrapper<CourseSession>()
                .eq(CourseSession::getCourseId, courseId)
                .orderByAsc(CourseSession::getStartTime));
    }

    /** 停售场次:只改状态不回补库存(名额还在,只是不可抢);Redis 侧由对账任务周期内摘 key */
    @Override
    public void closeSession(Long courseId, Long sessionId) {
        getMyCourse(courseId);   // 属主校验复用,新功能零成本获得防护
        int rows = courseSessionMapper.update(null, Wrappers.<CourseSession>lambdaUpdate()
                .eq(CourseSession::getId, sessionId)
                .eq(CourseSession::getCourseId, courseId)     // 防跨课改场次
                .eq(CourseSession::getStatus, CourseSession.STATUS_OPEN)
                .set(CourseSession::getStatus, CourseSession.STATUS_CLOSED));
        if (rows == 0) {
            throw new BusinessException("场次不存在或已停售");
        }
        notifyCourseChanged(courseId);   // 详情缓存失效 + ES 同步
    }

    @Override
    public String updateCover(Long courseId, MultipartFile file) {
        Course course = getMyCourse(courseId);          // 属主校验直接复用，新功能零成本获得防护
        String url = minioService.upload(file);
        course.setCover(url);
        courseMapper.updateById(course);
        notifyCourseChanged(courseId);
        return url;
    }

    /**
     * 取"属于自己的"课程：不存在、或存在但不是你的，统一报"课程不存在"。
     * 横向越权防护的核心：传承人只能操作自己名下的资源。
     */
    private Course getMyCourse(Long courseId) {
        Inheritor inheritor = inheritorService.currentInheritor();
        Course course = courseMapper.selectById(courseId);
        if (course == null || !course.getInheritorId().equals(inheritor.getId())) {
            throw new BusinessException("课程不存在");
        }
        return course;
    }

    @Override
    public void updateImages(Long courseId, List<MultipartFile> files) {
        Course course = getMyCourse(courseId);   // 属主校验,零成本获得
        if (files == null || files.isEmpty()) {
            throw new BusinessException("请选择至少一张图片");
        }
        List<String> urls = files.stream().map(minioService::upload).toList();
        course.setImages(JSONUtil.toJsonStr(urls));
        courseMapper.updateById(course);
    }
}