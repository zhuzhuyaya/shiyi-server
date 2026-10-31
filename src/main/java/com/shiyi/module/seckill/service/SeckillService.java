package com.shiyi.module.seckill.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shiyi.common.BusinessException;
import com.shiyi.module.course.entity.CourseSession;
import com.shiyi.module.course.mapper.CourseSessionMapper;
import com.shiyi.module.order.mapper.OrderMapper;
import com.shiyi.module.order.entity.Order;
import com.shiyi.module.seckill.vo.SeckillResultVO;

import com.shiyi.module.order.mq.GrabEvent;
import com.shiyi.module.order.mq.OrderMqConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillService {

    public static final String KEY_STOCK = "seckill:stock:";
    public static final String KEY_BUYER = "seckill:buyer:";

    // —— 抢购核心 Lua 在 Part 4 ——

    private final StringRedisTemplate stringRedisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final CourseSessionMapper courseSessionMapper;
    private final OrderMapper orderMapper;

    /** 库存预热:NX 初始化——key 已存在时绝不覆盖(否则把已扣减的库存加回去 = 超卖) */
    public void preheat(Long sessionId) {
        CourseSession session = courseSessionMapper.selectById(sessionId);
        if (session == null || session.getStatus() != CourseSession.STATUS_OPEN) {
            throw new BusinessException("场次不存在或未开放");
        }
        stringRedisTemplate.opsForValue().setIfAbsent(KEY_STOCK + sessionId,
                String.valueOf(session.getRemainingStock()));
    }

    /** 全量预热:Redis 重启丢数据后的修复入口 */
    public long preheatAll() {
        List<CourseSession> sessions = courseSessionMapper.selectList(
                Wrappers.<CourseSession>lambdaQuery()
                        .eq(CourseSession::getStatus, CourseSession.STATUS_OPEN));
        sessions.forEach(s -> stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_STOCK + s.getId(), String.valueOf(s.getRemainingStock())));
        return sessions.size();
    }

    /**
     * 抢课原子预扣,四种返回:
     *   1  成功(已扣 Redis 库存 + 已记买家,消息已由调用方发)
     *   0  已抢过(Redis Set 判重)
     *  -1  已抢完
     *  -2  未预热(key 不存在 = 场次未开放/不存在)
     * 查库存、判重、扣减三件事在一个脚本里原子完成——消灭 check 与 act 之间的竞态窗口
     */
    private static final String GRAB_LUA = """
            local stock = tonumber(redis.call('GET', KEYS[1]))
            if stock == nil then
                return -2
            end
            if stock <= 0 then
                return -1
            end
            if redis.call('SADD', KEYS[2], ARGV[1]) == 0 then
                return 0
            end
            redis.call('DECR', KEYS[1])
            return 1
            """;

    private static final DefaultRedisScript<Long> GRAB_SCRIPT = new DefaultRedisScript<>(GRAB_LUA, Long.class);

    /** 抢课热路径:零 DB 访问。限流(注解在 Controller)→ Lua 原子预扣 → 发 MQ */
    public void grab(Long sessionId) {
        long userId = StpUtil.getLoginIdAsLong();
        Long r = stringRedisTemplate.execute(GRAB_SCRIPT,
                List.of(KEY_STOCK + sessionId, KEY_BUYER + sessionId),
                String.valueOf(userId));
        if (r == null || r == -2) {
            throw new BusinessException("本场次不存在或未开放抢课");
        }
        if (r == -1) {
            throw new BusinessException("本场次已抢完");
        }
        if (r == 0) {
            throw new BusinessException("您已抢过本场次,一人一场一单");
        }
        rabbitTemplate.convertAndSend(OrderMqConfig.EXCHANGE_ORDER,
                OrderMqConfig.ROUTING_ORDER_CREATE, new GrabEvent(userId, sessionId));
        // 正常返回 = 排队中;结果看站内信 / 轮询结果接口
    }

    /** 抢课结果:有订单 → SUCCESS(带订单号);买家集合有我 → WAITING;都没有 → NOT_JOINED */
    public SeckillResultVO result(Long sessionId) {
        long userId = StpUtil.getLoginIdAsLong();
        Order order = orderMapper.selectOne(Wrappers.<Order>lambdaQuery()
                .eq(Order::getUserId, userId)
                .eq(Order::getSessionId, sessionId)
                .last("LIMIT 1"));   // 常量无注入面;uk_user_session 保证至多一单
        if (order != null) {
            return new SeckillResultVO(SeckillResultVO.SUCCESS, order.getId());
        }
        Boolean inQueue = stringRedisTemplate.opsForSet()
                .isMember(KEY_BUYER + sessionId, String.valueOf(userId));
        return Boolean.TRUE.equals(inQueue)
                ? new SeckillResultVO(SeckillResultVO.WAITING, null)
                : new SeckillResultVO(SeckillResultVO.NOT_JOINED, null);
    }
}
