package com.shiyi.module.search.mq;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_COURSE = "shiyi.es";
    public static final String QUEUE_COURSE_SYNC = "es.course.sync";

    /** 消息统一 JSON 序列化(默认 JDK 序列化——和 Redis 选型同一个理由) */
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public FanoutExchange courseExchange() {
        return new FanoutExchange(EXCHANGE_COURSE, true, false);
    }

    @Bean
    public Queue courseSyncQueue() {
        return QueueBuilder.durable(QUEUE_COURSE_SYNC).build();
    }

    @Bean
    public Binding courseSyncBinding() {
        return BindingBuilder.bind(courseSyncQueue()).to(courseExchange());
    }
}
