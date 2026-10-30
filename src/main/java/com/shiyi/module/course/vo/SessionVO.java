package com.shiyi.module.course.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SessionVO {
    private Long id;
    private LocalDateTime startTime;
    private Integer durationMin;
    private BigDecimal price;
    private Integer remainingStock;
}
