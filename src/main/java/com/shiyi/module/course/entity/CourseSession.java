package com.shiyi.module.course.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("course_session")
public class CourseSession {

    public static final int STATUS_CLOSED = 0;   // 停售
    public static final int STATUS_OPEN = 1;     // 报名中

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long courseId;
    private LocalDateTime startTime;
    private Integer durationMin;
    private BigDecimal price;
    private Integer capacity;
    private Integer remainingStock;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
