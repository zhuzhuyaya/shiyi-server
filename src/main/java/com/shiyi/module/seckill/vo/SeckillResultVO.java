package com.shiyi.module.seckill.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 抢课结果:status 用常量而非枚举,与 Order.STATUS_* 的项目风格保持一致 */
@Data
@AllArgsConstructor
public class SeckillResultVO {

    public static final String SUCCESS = "SUCCESS";       // 已出订单,orderId 有值
    public static final String WAITING = "WAITING";       // 在买家集合里,订单尚未落库
    public static final String NOT_JOINED = "NOT_JOINED"; // 没参与过这场

    private String status;
    private Long orderId;        // SUCCESS 时才有,前端拿去跳支付/订单详情
}
