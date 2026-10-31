package com.shiyi.module.auth.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.shiyi.common.BusinessException;
import com.shiyi.infra.MinioService;
import com.shiyi.module.auth.dto.LoginDTO;
import com.shiyi.module.auth.dto.RegisterDTO;
import com.shiyi.module.auth.service.AuthService;
import com.shiyi.module.auth.vo.LoginVO;
import com.shiyi.module.auth.vo.UserVO;
import com.shiyi.module.user.entity.User;
import com.shiyi.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final MinioService minioService;

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
        user.setAvatar("");
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

        // 密码对了才暴露"被禁用",不给撞库的人额外信息
        if (user.getStatus() != null && user.getStatus() != User.STATUS_ENABLED) {
            throw new BusinessException("账号已被禁用，请联系管理员");
        }

        StpUtil.login(user.getId());
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, user.getId())
                .set(User::getLastLoginAt, LocalDateTime.now()));
        return new LoginVO(StpUtil.getTokenName(), StpUtil.getTokenValue());
    }

    @Override
    public String adminResetPassword(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        // hutool randomString:小写字母+数字,8 位够演示用;生产应要求首次登录强制改密
        String temp = RandomUtil.randomString(8);
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getPasswordHash, BCrypt.hashpw(temp)));
        // 旧会话全部作废,拿到临时密码重新登录
        StpUtil.logout(userId);
        return temp;
    }

    @Override
    public UserVO me() {
        return BeanUtil.copyProperties(currentUser(), UserVO.class);
    }

    /** 当前登录用户:不存在说明登录态失效(如重启后内存会话被清),统一 401 话术 */
    private User currentUser() {
        User user = userMapper.selectById(StpUtil.getLoginIdAsLong());
        if (user == null) {
            throw new BusinessException(401, "登录已失效，请重新登录");
        }
        // 双保险:禁用在踢会话之外再拦一道,即便将来有"禁用不踢"的路径也兜得住
        if (user.getStatus() != null && user.getStatus() != User.STATUS_ENABLED) {
            throw new BusinessException(401, "账号已被禁用，请联系管理员");
        }
        return user;
    }

    @Override
    public void updateNickname(String nickname) {
        User user = currentUser();
        user.setNickname(nickname);
        userMapper.updateById(user);
    }

    @Override
    public String updateAvatar(MultipartFile file) {
        User user = currentUser();
        String url = minioService.upload(file);
        user.setAvatar(url);
        userMapper.updateById(user);
        return url;
    }

    @Override
    public void changePassword(String oldPassword, String newPassword) {
        User user = currentUser();
        if (!BCrypt.checkpw(oldPassword, user.getPasswordHash())) {
            throw new BusinessException("旧密码不正确");
        }
        user.setPasswordHash(BCrypt.hashpw(newPassword));
        userMapper.updateById(user);
        // 改密后全端下线强制重登:其他设备残留的会话必须全部作废
        StpUtil.logout(user.getId());
    }
}
