package com.shiyi.module.heritage.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("heritage_item")
public class HeritageItem {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String category;    // 门类：传统技艺/传统美术/传统戏剧…
    private String level;       // 级别：国家级/省级/市级
    private String region;      // 申报地区
    private String cover;
    private String description;
    private LocalDateTime createTime;
}
