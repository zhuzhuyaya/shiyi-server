package com.shiyi.module.auth.controller;

import com.shiyi.common.Result;
import com.shiyi.module.auth.dto.LoginDTO;
import com.shiyi.module.auth.dto.RegisterDTO;
import com.shiyi.module.auth.service.AuthService;
import com.shiyi.module.auth.vo.LoginVO;
import com.shiyi.module.auth.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterDTO dto) {
        authService.register(dto);
        return Result.ok();
    }

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.ok(authService.login(dto));
    }

    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.ok(authService.me());
    }
}
