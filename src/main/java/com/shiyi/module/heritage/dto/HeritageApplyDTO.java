package com.shiyi.module.heritage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 传承人申请收录新项目 */
@Data
public class HeritageApplyDTO {

    @NotBlank(message = "项目名称不能为空")
    @Size(max = 50, message = "项目名称最长 50 字")
    private String name;

    /** 项目简介,供管理员核实收录 */
    @Size(max = 500, message = "项目简介最长 500 字")
    private String description;
}
