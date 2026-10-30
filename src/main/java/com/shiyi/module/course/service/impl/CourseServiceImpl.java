package com.shiyi.module.course.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

    /** 课程变更后的一致性动作:删详情缓存(Cache-Aside)+ 发 ES 同步事件,收口一处防漏 */
    private void notifyCourseChanged(Long courseId) {
        stringRedisTemplate.delete("cache:course:detail:" + courseId);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_COURSE, "", new CourseSyncEvent(courseId));
    }

    @Override
    public void verify(Long courseId, boolean pass) {
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new BusinessException("课程不存在");
        }
        course.setStatus(pass ? Course.STATUS_ONLINE : Course.STATUS_OFFLINE);
        courseMapper.updateById(course);
        notifyCourseChanged(courseId);        // ← 加这行(顺手补上了第 5 课欠的"审核删缓存")
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
    }

    @Override
    public List<CourseSession> listSessions(Long courseId) {
        getMyCourse(courseId);
        return courseSessionMapper.selectList(new LambdaQueryWrapper<CourseSession>()
                .eq(CourseSession::getCourseId, courseId)
                .orderByAsc(CourseSession::getStartTime));
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
}