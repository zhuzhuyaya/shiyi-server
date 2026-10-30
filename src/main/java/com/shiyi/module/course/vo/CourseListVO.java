package com.shiyi.module.course.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CourseListVO {
    private Long id;
    private String title;
    private String cover;
    private String tags;
    private String address;
    private BigDecimal minPrice;
    private Integer sales;
    private String inheritorName;
    private String heritageName;
}
