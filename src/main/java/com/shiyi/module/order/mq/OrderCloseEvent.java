package com.shiyi.module.order.mq;

/** 订单延迟关单事件:只带 ID,消费端回查最新状态 */
public record OrderCloseEvent(Long orderId) {
}
