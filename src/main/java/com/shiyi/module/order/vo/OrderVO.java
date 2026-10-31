package com.shiyi.module.order.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OrderVO {
    private Long id;
    private String orderNo;
    private String courseTitle;
    private LocalDateTime startTime;   // 场次开课时间
    private BigDecimal amount;
    private Integer status;
    private String checkinCode;        // 已支付才有
    private LocalDateTime createTime;
}
