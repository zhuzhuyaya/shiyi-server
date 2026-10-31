package com.shiyi.module.order.mq;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OrderMqConfig {

    public static final String EXCHANGE_ORDER = "shiyi.order";
    public static final String QUEUE_ORDER_CREATE = "order.create";
    public static final String ROUTING_ORDER_CREATE = "order.create";
    public static final String QUEUE_ORDER_WAIT = "order.wait";
    public static final String ROUTING_ORDER_WAIT = "order.wait";
    public static final String QUEUE_ORDER_CLOSE = "order.close";
    public static final String ROUTING_ORDER_CLOSE = "order.close";

    @Bean
    public DirectExchange orderExchange() {
        return new DirectExchange(EXCHANGE_ORDER, true, false);
    }

    @Bean
    public Queue orderCreateQueue() {
        return QueueBuilder.durable(QUEUE_ORDER_CREATE).build();
    }

    @Bean
    public Binding orderCreateBinding() {
        return BindingBuilder.bind(orderCreateQueue())
                .to(orderExchange()).with(ROUTING_ORDER_CREATE);
    }

    /**
     * 等待队列:无消费者,消息躺满 TTL 后死信转投 order.close。
     * TTL 做成配置:演示时改 1 分钟,生产 15 分钟。
     */
    @Value("${shiyi.order.close-ttl-ms:900000}")
    private int closeTtlMs;

    @Bean
    public Queue orderWaitQueue() {
        return QueueBuilder.durable(QUEUE_ORDER_WAIT)
                .ttl(closeTtlMs)
                .deadLetterExchange(EXCHANGE_ORDER)
                .deadLetterRoutingKey(ROUTING_ORDER_CLOSE)
                .build();
    }

    @Bean
    public Queue orderCloseQueue() {
        return QueueBuilder.durable(QUEUE_ORDER_CLOSE).build();
    }

    /** 等待队列的入口绑定:漏了它,DirectExchange 对 order.wait 路由键的消息会静默丢弃,延迟关单永远不触发 */
    @Bean
    public Binding orderWaitBinding() {
        return BindingBuilder.bind(orderWaitQueue())
                .to(orderExchange()).with(ROUTING_ORDER_WAIT);
    }

    @Bean
    public Binding orderCloseBinding() {
        return BindingBuilder.bind(orderCloseQueue())
                .to(orderExchange()).with(ROUTING_ORDER_CLOSE);
    }
}