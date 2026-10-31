package com.shiyi.module.heritage.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("heritage_item")
public class HeritageItem {

    /** 用户随入驻申请提交的新项目,管理员收录后前台可见 */
    public static final int STATUS_PENDING = 0;
    public static final int STATUS_ACTIVE = 1;
    public static final int STATUS_DISABLED = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String category;    // 门类：传统技艺/传统美术/传统戏剧…
    private String level;       // 级别：国家级/省级/市级
    private String region;      // 申报地区
    private String cover;
    private String description;
    private Integer status;     // 0 待收录 1 已收录 2 已停用
    private LocalDateTime createTime;
}
