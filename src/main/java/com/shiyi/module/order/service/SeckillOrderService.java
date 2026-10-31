package com.shiyi.module.order.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shiyi.module.course.entity.CourseSession;
import com.shiyi.module.course.mapper.CourseSessionMapper;
import com.shiyi.module.order.entity.Order;
import com.shiyi.module.order.mapper.OrderMapper;
import com.shiyi.module.order.mq.OrderCloseEvent;
import com.shiyi.module.order.mq.OrderMqConfig;
import com.shiyi.module.seckill.service.SeckillService;
import com.shiyi.module.user.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillOrderService {

    private final OrderMapper orderMapper;
    private final CourseSessionMapper courseSessionMapper;
    private final MessageService messageService;
    private final StringRedisTemplate stringRedisTemplate;
    private final RabbitTemplate rabbitTemplate;

    /**
     * MQ 消费:事务落库。三道防线:
     * 前置查询挡消息重投 / WHERE remaining_stock > 0 挡超卖 / 唯一索引挡极端并发
     */
    @Transactional(rollbackFor = Exception.class)
    public void createOrder(long userId, Long sessionId) {
        // ① 幂等:消息重复投递/重复抢,已有订单直接确认消费(最常见,必须先挡)
        Long existing = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .eq(Order::getSessionId, sessionId));
        if (existing != null && existing > 0) {
            log.info("重复消息,已有订单,忽略 userId={} sessionId={}", userId, sessionId);
            return;
        }

        // ② DB 兜底扣库存:Redis 说了不算,这里说了算
        int rows = courseSessionMapper.deductStock(sessionId);
        if (rows == 0) {
            // Redis 与 DB 不一致(如缓存被人改过/预热失真):以 DB 为准修复
            CourseSession session = courseSessionMapper.selectById(sessionId);
            stringRedisTemplate.opsForValue().set(SeckillService.KEY_STOCK + sessionId,
                    String.valueOf(session.getRemainingStock()));   // SET 真实值,不 INCR 盲加
            stringRedisTemplate.opsForSet().remove(SeckillService.KEY_BUYER + sessionId,
                    String.valueOf(userId));
            messageService.send(userId, "抢课失败", "本场次名额已抢完,欢迎下次再来");
            return;   // 正常返回 = 消息正常确认,失败结果已用站内信送达
        }

        // ③ 建订单(uk_user_session 唯一索引是最后防线,极端并发撞上会抛异常回滚重投,下次 ① 兜住)
        CourseSession session = courseSessionMapper.selectById(sessionId);
        Order order = new Order();
        order.setOrderNo(String.valueOf(IdUtil.getSnowflakeNextId()));
        order.setUserId(userId);
        order.setSessionId(sessionId);
        order.setCourseId(session.getCourseId());
        order.setQuantity(1);
        order.setAmount(session.getPrice());   // 金额快照:下单时的价格,之后调价不影响此单
        order.setStatus(Order.STATUS_PENDING_PAY);
        orderMapper.insert(order);
        // 一致性动作(同 checkin):库存变了,课程详情缓存必须失效,否则详情页名额一直是旧值
        stringRedisTemplate.delete("cache:course:detail:" + session.getCourseId());
        // 挂 15 分钟延迟关单:进等待队列躺满 TTL,死信转投 order.close
        rabbitTemplate.convertAndSend(OrderMqConfig.EXCHANGE_ORDER,
                OrderMqConfig.ROUTING_ORDER_WAIT, new OrderCloseEvent(order.getId()));

        messageService.send(userId, "抢课成功",
                "订单 " + order.getOrderNo() + " 已创建,请在 15 分钟内完成支付");
    }
}
