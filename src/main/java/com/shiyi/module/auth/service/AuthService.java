package com.shiyi.module.auth.service;

import com.shiyi.module.auth.dto.LoginDTO;
import com.shiyi.module.auth.dto.RegisterDTO;
import com.shiyi.module.auth.vo.LoginVO;
import com.shiyi.module.auth.vo.UserVO;

public interface AuthService {
    void register(RegisterDTO dto);
    LoginVO login(LoginDTO dto);
    UserVO me();
}
