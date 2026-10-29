package com.shiyi.module.auth.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginVO {
    private String tokenName;
    private String tokenValue;
}
