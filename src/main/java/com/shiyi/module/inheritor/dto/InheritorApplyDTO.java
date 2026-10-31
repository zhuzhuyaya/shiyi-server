package com.shiyi.module.inheritor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class InheritorApplyDTO {

    @NotBlank(message = "工坊名称不能为空")
    @Size(max = 50, message = "名称最长 50 字")
    private String name;

    /** 与 newHeritageName 二选一:选已有项目,或申请收录新项目 */
    private Long heritageItemId;

    /** 字典里没有的项目,随申请提交待管理员收录 */
    @Size(max = 50, message = "项目名称最长 50 字")
    private String newHeritageName;

    /** 新项目简介,供管理员核实收录 */
    @Size(max = 500, message = "项目简介最长 500 字")
    private String newHeritageDesc;

    @Size(max = 2000, message = "简介最长 2000 字")
    private String bio;

    @NotBlank(message = "联系方式不能为空")
    @Size(max = 50)
    private String contact;
}
