package com.shiyi.module.course.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("course")
public class Course {

    public static final int STATUS_PENDING = 0;   // 待审核
    public static final int STATUS_ONLINE = 1;    // 上架
    public static final int STATUS_OFFLINE = 2;   // 下架

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long inheritorId;
    private Long heritageItemId;
    private String title;
    private String cover;          // MinIO 上传后回填（下半场）
    private String images;         // JSON 数组字符串（同上）
    private String description;
    private String tags;
    private String address;
    private BigDecimal lng;        // DECIMAL 对应 BigDecimal，别用 double
    private BigDecimal lat;
    private Integer status;
    /** 驳回原因;手动下架时为空,用于区分"审核未通过"和"被下架" */
    private String rejectReason;
    private Integer sales;

    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}