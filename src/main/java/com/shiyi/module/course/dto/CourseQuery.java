package com.shiyi.module.course.dto;

import lombok.Data;

@Data
public class CourseQuery {
    private long page = 1;
    private long size = 10;
    private Long heritageItemId;
    private String keyword;
}
