package com.shiyi.module.order.mq;

import com.shiyi.module.order.service.SeckillOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCreateListener {

    private final SeckillOrderService seckillOrderService;

    @RabbitListener(queues = OrderMqConfig.QUEUE_ORDER_CREATE)
    public void onGrab(GrabEvent event) {
        log.info("收到抢课落库消息 userId={} sessionId={}", event.userId(), event.sessionId());
        seckillOrderService.createOrder(event.userId(), event.sessionId());
    }
}
