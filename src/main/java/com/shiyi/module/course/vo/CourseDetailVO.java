package com.shiyi.module.course.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CourseDetailVO {
    private Long id;
    private String title;
    private String cover;
    private List<String> images;    // 库里是 JSON 字符串,这里解析成真数组
    private String description;
    private String tags;
    private String address;
    private BigDecimal lng;
    private BigDecimal lat;
    private Integer sales;
    private String inheritorName;
    private String heritageName;
    private List<SessionVO> sessions;  // 未开场的场次
}
