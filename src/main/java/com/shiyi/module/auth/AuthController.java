package com.shiyi.module.auth;

import com.shiyi.common.BusinessException;
import com.shiyi.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @GetMapping("/ping")
    public Result<String> ping() {
        return Result.ok("pong");
    }

    @GetMapping("/err")
    public Result<Void> err() {
        throw new BusinessException("演示：手机号已被注册");
    }
}
