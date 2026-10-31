package com.shiyi.module.order.mq;

import com.shiyi.module.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCloseListener {

    private final OrderService orderService;

    @RabbitListener(queues = OrderMqConfig.QUEUE_ORDER_CLOSE)
    public void onClose(OrderCloseEvent event) {
        log.info("收到延迟关单消息 orderId={}", event.orderId());
        orderService.closeOrder(event.orderId());
    }
}