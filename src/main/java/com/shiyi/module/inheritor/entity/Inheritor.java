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
    /** 资质证书照片 URL 数组,JSON 字符串(存法同 Course.images) */
    private String certImages;
    private Integer verifyStatus;
    /** 驳回原因,通过/重提时清空 */
    private String rejectReason;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
