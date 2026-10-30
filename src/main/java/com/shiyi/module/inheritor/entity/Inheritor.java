package com.shiyi.module.inheritor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("inheritor")
public class Inheritor {

    public static final int VERIFY_PENDING = 0;
    public static final int VERIFY_PASSED = 1;
    public static final int VERIFY_REJECTED = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String name;
    private Long heritageItemId;
    private String cover;
    private String bio;
    private String contact;
    private Integer verifyStatus;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
