package com.shiyi.module.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CourseCreateDTO {

    @NotNull(message = "请选择关联的非遗项目")
    private Long heritageItemId;

    @NotBlank(message = "课程标题不能为空")
    @Size(max = 100, message = "标题最长 100 字")
    private String title;

    @NotBlank(message = "上课地址不能为空")
    private String address;

    @NotNull(message = "请填写经度")
    private BigDecimal lng;

    @NotNull(message = "请填写纬度")
    private BigDecimal lat;

    private String description;
    private String tags;
}