package com.shiyi.module.auth.service;

import com.shiyi.module.auth.dto.LoginDTO;
import com.shiyi.module.auth.dto.RegisterDTO;
import com.shiyi.module.auth.vo.LoginVO;
import com.shiyi.module.auth.vo.UserVO;
import org.springframework.web.multipart.MultipartFile;

public interface AuthService {
    void register(RegisterDTO dto);
    LoginVO login(LoginDTO dto);
    UserVO me();
    void updateNickname(String nickname);
    String updateAvatar(MultipartFile file);
    void changePassword(String oldPassword, String newPassword);

    /** 管理端重置密码:生成临时密码返回一次(由管理员转交),旧会话全部作废 */
    String adminResetPassword(Long userId);
}
