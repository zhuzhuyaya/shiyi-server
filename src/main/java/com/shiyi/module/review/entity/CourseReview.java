package com.shiyi.module.review.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("course_review")
public class CourseReview {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;        // 一单一评:uk_order 唯一索引兜底
    private Long userId;
    private Long courseId;       // 冗余:评价列表免联表
    private Integer rating;      // 1~5
    private String content;
    private String images;       // JSON 数组,晒图
    private String reply;        // 传承人回复
    private Integer deleted;

    private LocalDateTime createTime;
}
