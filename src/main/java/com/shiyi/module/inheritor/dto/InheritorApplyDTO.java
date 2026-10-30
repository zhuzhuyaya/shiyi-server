package com.shiyi.module.inheritor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class InheritorApplyDTO {

    @NotBlank(message = "工坊名称不能为空")
    @Size(max = 50, message = "名称最长 50 字")
    private String name;

    @NotNull(message = "请选择主攻非遗项目")
    private Long heritageItemId;

    @Size(max = 2000, message = "简介最长 2000 字")
    private String bio;

    @NotBlank(message = "联系方式不能为空")
    @Size(max = 50)
    private String contact;
}
