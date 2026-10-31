package com.shiyi.module.course.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shiyi.common.BusinessException;
import com.shiyi.module.course.dto.CourseQuery;
import com.shiyi.module.course.entity.Course;
import com.shiyi.module.course.entity.CourseSession;
import com.shiyi.module.course.mapper.CourseMapper;
import com.shiyi.module.course.mapper.CourseSessionMapper;
import com.shiyi.module.course.vo.CourseDetailVO;
import com.shiyi.module.course.vo.CourseListVO;
import com.shiyi.module.course.vo.SessionVO;
import com.shiyi.module.heritage.entity.HeritageItem;
import com.shiyi.module.heritage.mapper.HeritageItemMapper;
import com.shiyi.module.inheritor.entity.Inheritor;
import com.shiyi.module.inheritor.mapper.InheritorMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CoursePortalService {

    private final CourseMapper courseMapper;
    private final CourseSessionMapper courseSessionMapper;
    private final InheritorMapper inheritorMapper;
    private final HeritageItemMapper heritageItemMapper;
    private final StringRedisTemplate stringRedisTemplate;

    /** C 端课程列表:分页 + 筛选,只卖上架的 */
    public Page<CourseListVO> listForPortal(CourseQuery query) {
        // category 在 heritage_item 表:先查出门类下的项目 id 集合,再按 id 筛选课程
        List<Long> heritageIdsForCategory = null;
        if (StrUtil.isNotBlank(query.getCategory())) {
            heritageIdsForCategory = heritageItemMapper.selectList(Wrappers.<HeritageItem>lambdaQuery()
                            .eq(HeritageItem::getCategory, query.getCategory()))
                    .stream().map(HeritageItem::getId).toList();
        }

        Page<Course> page = courseMapper.selectPage(new Page<>(query.getPage(), query.getSize()),
                Wrappers.<Course>lambdaQuery()
                        .eq(Course::getStatus, Course.STATUS_ONLINE)   // 闸门:未审核/下架的 C 端不可见
                        .eq(query.getHeritageItemId() != null, Course::getHeritageItemId, query.getHeritageItemId())
                        .in(heritageIdsForCategory != null && !heritageIdsForCategory.isEmpty(),
                                Course::getHeritageItemId, heritageIdsForCategory)
                        .like(StrUtil.isNotBlank(query.getKeyword()), Course::getTitle, query.getKeyword())
                        .orderByDesc(Course::getSales));

        Page<CourseListVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(assemble(page.getRecords()));
        return voPage;
    }

    /** 首页热门榜：销量 Top10，纯读容忍旧数据 */
    public List<CourseListVO> hotList() {
        List<Course> courses = courseMapper.selectList(Wrappers.<Course>lambdaQuery()
                .eq(Course::getStatus, Course.STATUS_ONLINE)
                .orderByDesc(Course::getSales)
                .last("LIMIT 10"));
        return assemble(courses);
    }

    /** 批量组装:3 次固定查询替代 1+N 次逐条查 */
    private List<CourseListVO> assemble(List<Course> courses) {
        if (courses.isEmpty()) {
            return List.of();
        }
        List<Long> courseIds = courses.stream().map(Course::getId).toList();
        List<Long> inheritorIds = courses.stream().map(Course::getInheritorId).distinct().toList();
        List<Long> heritageIds = courses.stream().map(Course::getHeritageItemId).distinct().toList();

        Map<Long, String> inheritorNames = inheritorMapper.selectBatchIds(inheritorIds).stream()
                .collect(Collectors.toMap(Inheritor::getId, Inheritor::getName));
        Map<Long, String> heritageNames = heritageItemMapper.selectBatchIds(heritageIds).stream()
                .collect(Collectors.toMap(HeritageItem::getId, HeritageItem::getName));
        // toMap 第三参数 = key 冲突时的合并函数：一门课多场，取最低价
        Map<Long, BigDecimal> minPrices = courseSessionMapper.selectList(
                        Wrappers.<CourseSession>lambdaQuery().in(CourseSession::getCourseId, courseIds))
                .stream()
                .collect(Collectors.toMap(CourseSession::getCourseId, CourseSession::getPrice, BigDecimal::min));

        return courses.stream().map(course -> {
            CourseListVO vo = BeanUtil.toBean(course, CourseListVO.class);
            vo.setInheritorName(inheritorNames.get(course.getInheritorId()));
            vo.setHeritageName(heritageNames.get(course.getHeritageItemId()));
            vo.setMinPrice(minPrices.get(course.getId()));
            return vo;
        }).toList();
    }

    // ==================== 详情（缓存三件套） ====================

    /** C 端详情：缓存空值防穿透 + 互斥锁防击穿 + 随机 TTL 防雪崩 */
    public CourseDetailVO getDetail(Long courseId) {
        String key = "cache:course:detail:" + courseId;

        // ① 先查缓存
        String cached = stringRedisTemplate.opsForValue().get(key);
        if (cached != null) {
            if (StrUtil.isEmpty(cached)) {
                // 命中空值标记：确认不存在，直接挡掉，不打 DB（防穿透）
                throw new BusinessException("课程不存在");
            }
            return JSONUtil.toBean(cached, CourseDetailVO.class);
        }

        // ② 缓存未命中 → 抢互斥锁，只放一个线程去查库重建（防击穿）
        String lockKey = "lock:course:detail:" + courseId;
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", Duration.ofSeconds(10));   // SET NX EX
        if (!Boolean.TRUE.equals(locked)) {
            // 没抢到锁：别的线程正在查库，睡 50ms 回头重读缓存
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return getDetail(courseId);
        }

        try {
            Course course = courseMapper.selectById(courseId);
            // 不存在或未上架 → 统一话术防探测，并缓存空值（短 TTL）
            if (course == null || course.getStatus() != Course.STATUS_ONLINE) {
                stringRedisTemplate.opsForValue().set(key, "", Duration.ofMinutes(2));
                throw new BusinessException("课程不存在");
            }
            CourseDetailVO vo = buildDetail(course);
            // TTL = 30min + 0~300 随机秒（防雪崩）
            long ttl = 30 * 60 + RandomUtil.randomInt(0, 300);
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(vo), Duration.ofSeconds(ttl));
            return vo;
        } finally {
            stringRedisTemplate.delete(lockKey);
        }
    }

    private CourseDetailVO buildDetail(Course course) {
        CourseDetailVO vo = BeanUtil.toBean(course, CourseDetailVO.class);
        if (StrUtil.isNotBlank(course.getImages())) {
            vo.setImages(JSONUtil.toList(course.getImages(), String.class));
        }
        vo.setInheritorName(inheritorMapper.selectById(course.getInheritorId()).getName());
        vo.setHeritageName(heritageItemMapper.selectById(course.getHeritageItemId()).getName());
        // 闸门：C 端只看未开场的场次
        vo.setSessions(courseSessionMapper.selectList(Wrappers.<CourseSession>lambdaQuery()
                        .eq(CourseSession::getCourseId, course.getId())
                        .gt(CourseSession::getStartTime, LocalDateTime.now())
                        .orderByAsc(CourseSession::getStartTime))
                .stream().map(s -> BeanUtil.toBean(s, SessionVO.class)).toList());
        return vo;
    }
}
