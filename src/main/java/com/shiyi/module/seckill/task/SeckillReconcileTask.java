package com.shiyi.module.seckill.task;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shiyi.module.course.entity.CourseSession;
import com.shiyi.module.course.mapper.CourseSessionMapper;
import com.shiyi.module.seckill.service.SeckillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 抢课库存对账:DB 是真相,Redis 是投影。
 * 下架:已开场 / 已停售的场次删 stock key,grab 的 Lua 查不到 key 返回 -2,抢购自然关闭
 * 修复:未开场 + 开放 + 有库存,但 stock key 丢失(Redis 重启等)→ setIfAbsent 按 DB 真值补回
 * 只动 Redis 不动 DB;已抢完(stock=0)的 key 刻意保留,前端语义是"已抢完"而非"未开放"
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillReconcileTask {

    private final CourseSessionMapper courseSessionMapper;
    private final StringRedisTemplate stringRedisTemplate;

    /** initialDelay 留 30s,避开启动高峰;fixedDelay 上一轮跑完再计时,不会堆积 */
    @Scheduled(fixedDelay = 5 * 60 * 1000, initialDelay = 30 * 1000)
    public void reconcile() {
        LocalDateTime now = LocalDateTime.now();
        int removed = 0;

        // 1) 已开场的开放场次 → 下架
        List<CourseSession> started = courseSessionMapper.selectList(
                Wrappers.<CourseSession>lambdaQuery()
                        .lt(CourseSession::getStartTime, now)
                        .eq(CourseSession::getStatus, CourseSession.STATUS_OPEN));
        // 2) 已停售的场次 → 下架(停售只改了 DB,Redis 在这里跟进)
        List<CourseSession> closed = courseSessionMapper.selectList(
                Wrappers.<CourseSession>lambdaQuery()
                        .eq(CourseSession::getStatus, CourseSession.STATUS_CLOSED));
        for (CourseSession s : started) {
            if (Boolean.TRUE.equals(stringRedisTemplate.delete(SeckillService.KEY_STOCK + s.getId()))) {
                removed++;
            }
        }
        for (CourseSession s : closed) {
            if (Boolean.TRUE.equals(stringRedisTemplate.delete(SeckillService.KEY_STOCK + s.getId()))) {
                removed++;
            }
        }
        if (removed > 0) {
            log.info("抢课对账:{} 个场次已开场/停售,stock key 已下架", removed);
        }

        // 3) 未开场 + 开放 + 有库存,key 丢失才补;setIfAbsent 保证绝不覆盖正在扣减的实时库存
        List<CourseSession> upcoming = courseSessionMapper.selectList(
                Wrappers.<CourseSession>lambdaQuery()
                        .gt(CourseSession::getStartTime, now)
                        .eq(CourseSession::getStatus, CourseSession.STATUS_OPEN)
                        .gt(CourseSession::getRemainingStock, 0));
        int repaired = 0;
        for (CourseSession s : upcoming) {
            Boolean created = stringRedisTemplate.opsForValue().setIfAbsent(
                    SeckillService.KEY_STOCK + s.getId(),
                    String.valueOf(s.getRemainingStock()));
            if (Boolean.TRUE.equals(created)) {
                repaired++;
                log.warn("抢课对账:场次 {} stock key 缺失,按 DB 真值 {} 补回",
                        s.getId(), s.getRemainingStock());
            }
        }
        if (repaired > 0) {
            log.info("抢课对账:{} 个场次 stock key 已修复", repaired);
        }
    }
}
