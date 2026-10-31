package com.shiyi.module.order.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CheckinDTO {

    @NotBlank(message = "核销码不能为空")
    private String code;
}
