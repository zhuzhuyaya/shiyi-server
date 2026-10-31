package com.shiyi.module.order.mq;

/** 抢课成功事件:Lua 预扣已通过,待落库(只带 ID,消费端回查,同第 6 课的取舍) */
public record GrabEvent(long userId, Long sessionId) {
}
