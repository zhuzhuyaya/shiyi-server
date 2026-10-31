package com.shiyi.module.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("order_info")
public class Order {

    public static final int STATUS_PENDING_PAY = 0;   // 待支付
    public static final int STATUS_PAID = 1;          // 已支付
    public static final int STATUS_CHECKED_IN = 2;    // 已核销(到店上课)
    public static final int STATUS_CANCELLED = 3;     // 已取消(超时未付)
    public static final int STATUS_REFUNDED = 4;      // 已退款

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;
    private Long userId;
    private Long sessionId;
    private Long courseId;       // 冗余:评价/列表免联表
    private Integer quantity;    // 固定 1,一人一场一单
    private BigDecimal amount;   // 下单时价格快照
    private Integer status;
    private LocalDateTime payTime;
    private String checkinCode;  // 支付后生成,第 8 课
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
