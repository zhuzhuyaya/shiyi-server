package com.shiyi.module.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shiyi.common.Result;
import com.shiyi.module.user.entity.User;
import com.shiyi.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserMapper userMapper;

    @GetMapping
    public Result<Page<User>> list(long page, long size) {
        Page<User> result = userMapper.selectPage(new Page<>(page,size),null);
        return Result.ok(result);
    }

    @GetMapping("/inheritors")
    public Result<java.util.List<User>> inheritors() {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .eq(User::getRole, "INHERITOR");
        return Result.ok(userMapper.selectList(wrapper));
    }
}
