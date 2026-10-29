package com.shiyi.module.user;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shiyi.common.Result;
import com.shiyi.module.auth.vo.UserVO;
import com.shiyi.module.user.entity.User;
import com.shiyi.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserMapper userMapper;

    @SaCheckRole("ADMIN")
    @GetMapping
    public Result<Page<UserVO>> list(@RequestParam(defaultValue = "1") long page,
                                     @RequestParam(defaultValue = "10") long size) {
        Page<User> p = userMapper.selectPage(new Page<>(page, size), null);
        Page<UserVO> vo = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        vo.setRecords(BeanUtil.copyToList(p.getRecords(), UserVO.class));
        return Result.ok(vo);
    }

    @SaCheckRole("ADMIN")
    @GetMapping("/inheritors")
    public Result<List<UserVO>> inheritors() {
        List<User> users = userMapper.selectList(
                new LambdaQueryWrapper<User>().eq(User::getRole, "INHERITOR"));
        return Result.ok(BeanUtil.copyToList(users, UserVO.class));
    }
}
