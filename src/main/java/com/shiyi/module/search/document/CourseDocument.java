package com.shiyi.module.search.document;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CourseDocument {
    private Long id;
    private String title;
    private String cover;
    private String tags;
    private String description;   // 只用于匹配,不展示
    private String address;
    private Long inheritorId;
    private String inheritorName;
    private Long heritageItemId;
    private String heritageName;
    private BigDecimal minPrice;
    private Integer sales;
    private Integer status;
}
