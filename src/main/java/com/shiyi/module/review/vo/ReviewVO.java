package com.shiyi.module.review.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReviewVO {
    private Long id;
    private String nickname;     // 评价者
    private Integer rating;
    private String content;
    private String images;
    private String reply;        // 传承人回复,空=未回复
    private LocalDateTime createTime;
}