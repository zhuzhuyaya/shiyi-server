package com.shiyi.module.course.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CourseSessionCreateDTO {

    @NotNull(message = "开课时间不能为空")
    @Future(message = "开课时间必须在未来")
    private LocalDateTime startTime;

    @NotNull(message = "时长不能为空")
    @Min(value = 30, message = "时长至少 30 分钟")
    private Integer durationMin;

    @NotNull(message = "价格不能为空")
    @DecimalMin(value = "0", message = "价格不能为负")
    private BigDecimal price;

    @NotNull(message = "名额不能为空")
    @Min(value = 1, message = "名额至少 1 个")
    @Max(value = 500, message = "名额过多")
    private Integer capacity;
}
