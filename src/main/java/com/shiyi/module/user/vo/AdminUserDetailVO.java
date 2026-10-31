package com.shiyi.module.user.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 管理端用户详情:基本资料 + 消费画像 + 传承人档案(非传承人为 null) */
@Data
public class AdminUserDetailVO {
    private Long id;
    private String phone;
    private String nickname;
    private String avatar;
    private String role;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime lastLoginAt;

    private Long orderCount;      // 全部订单
    private Long paidCount;       // 已支付/已核销订单
    private BigDecimal totalSpent;

    private Long inheritorId;
    private String inheritorName;
    private Integer inheritorVerifyStatus;
}
