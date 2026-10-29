package com.shiyi.module.auth.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shiyi.common.BusinessException;
import com.shiyi.module.auth.dto.LoginDTO;
import com.shiyi.module.auth.dto.RegisterDTO;
import com.shiyi.module.auth.service.AuthService;
import com.shiyi.module.auth.vo.LoginVO;
import com.shiyi.module.auth.vo.UserVO;
import com.shiyi.module.user.entity.User;
import com.shiyi.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;

    @Override
    public void register(RegisterDTO dto) {
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getPhone,dto.getPhone())
        );

        if (count > 0) {
            throw new BusinessException("手机号已被注册");
        }

        User user = new User();
        user.setPhone(dto.getPhone());
        user.setNickname(dto.getNickname());
        user.setRole("USER");

        user.setPasswordHash(BCrypt.hashpw(dto.getPassword()));
        userMapper.insert(user);
    }

    @Override
    public LoginVO login(LoginDTO dto) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getPhone, dto.getPhone()));

        if (user == null || user.getPasswordHash() == null || user.getPasswordHash().isBlank()
                || !BCrypt.checkpw(dto.getPassword(), user.getPasswordHash())) {
            // 故意不区分"用户不存在"和"密码错误"，防止撞库探测
            throw new BusinessException("手机号或密码错误");
        }

        StpUtil.login(user.getId());
        return new LoginVO(StpUtil.getTokenName(), StpUtil.getTokenValue());
    }

    @Override
    public UserVO me() {
        User user = userMapper.selectById(StpUtil.getLoginIdAsLong());
        if (user == null) {
            throw new BusinessException(401, "登录已失效，请重新登录");
        }
        return BeanUtil.copyProperties(user, UserVO.class);
    }
}
