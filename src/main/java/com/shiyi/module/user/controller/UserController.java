package com.shiyi.module.user.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shiyi.common.BusinessException;
import com.shiyi.common.Result;
import com.shiyi.module.auth.service.AuthService;
import com.shiyi.module.auth.vo.UserVO;
import com.shiyi.module.inheritor.entity.Inheritor;
import com.shiyi.module.inheritor.mapper.InheritorMapper;
import com.shiyi.module.order.entity.Order;
import com.shiyi.module.order.mapper.OrderMapper;
import com.shiyi.module.user.dto.ChangePasswordDTO;
import com.shiyi.module.user.dto.UpdateNicknameDTO;
import com.shiyi.module.user.entity.User;
import com.shiyi.module.user.mapper.UserMapper;
import com.shiyi.module.user.vo.AdminUserDetailVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserMapper userMapper;
    private final AuthService authService;
    private final OrderMapper orderMapper;
    private final InheritorMapper inheritorMapper;

    /** 个人中心:改昵称 */
    @PutMapping("/me")
    public Result<Void> updateNickname(@Valid @RequestBody UpdateNicknameDTO dto) {
        authService.updateNickname(dto.getNickname());
        return Result.ok();
    }

    /** 个人中心:换头像(MultipartFile → MinIO,白名单校验复用) */
    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<String> avatar(@RequestParam("file") MultipartFile file) {
        return Result.ok(authService.updateAvatar(file));
    }

    /** 个人中心:改密码,成功后全端下线强制重登 */
    @PutMapping("/me/password")
    public Result<Void> password(@Valid @RequestBody ChangePasswordDTO dto) {
        authService.changePassword(dto.getOldPassword(), dto.getNewPassword());
        return Result.ok();
    }

    @SaCheckRole("ADMIN")
    @GetMapping
    public Result<Page<UserVO>> list(@RequestParam(defaultValue = "1") long page,
                                     @RequestParam(defaultValue = "10") long size,
                                     @RequestParam(required = false) String keyword,
                                     @RequestParam(required = false) String role) {
        LambdaQueryWrapper<User> qw = new LambdaQueryWrapper<User>().orderByDesc(User::getId);
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            qw.and(w -> w.like(User::getPhone, kw).or().like(User::getNickname, kw));
        }
        if (role != null && !role.isBlank()) {
            qw.eq(User::getRole, role);
        }
        Page<User> p = userMapper.selectPage(new Page<>(page, size), qw);
        Page<UserVO> vo = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        vo.setRecords(BeanUtil.copyToList(p.getRecords(), UserVO.class));
        return Result.ok(vo);
    }

    /** 启用/禁用:禁用同时踢下线——请求链路不查 status,不踢的话旧 token 照常能用 */
    @SaCheckRole("ADMIN")
    @PutMapping("/{id}/status")
    public Result<Void> setStatus(@PathVariable Long id, @RequestParam boolean enabled) {
        if (id.equals(StpUtil.getLoginIdAsLong())) {
            throw new BusinessException("不能操作自己的账号");
        }
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, id)
                .set(User::getStatus, enabled ? User.STATUS_ENABLED : User.STATUS_DISABLED));
        if (!enabled) {
            StpUtil.logout(id);
        }
        return Result.ok();
    }

    /** 调整角色:角色每次请求实时读库(StpInterfaceImpl),改完即生效 */
    @SaCheckRole("ADMIN")
    @PutMapping("/{id}/role")
    public Result<Void> setRole(@PathVariable Long id, @RequestParam String role) {
        if (!List.of("USER", "INHERITOR", "ADMIN").contains(role)) {
            throw new BusinessException("未知的角色");
        }
        if (id.equals(StpUtil.getLoginIdAsLong())) {
            throw new BusinessException("不能调整自己的角色");
        }
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, id)
                .set(User::getRole, role));
        return Result.ok();
    }

    /** 用户详情:基本资料 + 订单消费画像 + 传承人档案 */
    @SaCheckRole("ADMIN")
    @GetMapping("/{id}/detail")
    public Result<AdminUserDetailVO> detail(@PathVariable Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        AdminUserDetailVO vo = BeanUtil.copyProperties(user, AdminUserDetailVO.class);
        vo.setOrderCount(orderMapper.selectCount(
                new LambdaQueryWrapper<Order>().eq(Order::getUserId, id)));
        List<Integer> paidStatus = List.of(Order.STATUS_PAID, Order.STATUS_CHECKED_IN);
        vo.setPaidCount(orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, id).in(Order::getStatus, paidStatus)));
        // 退款订单不计入消费;SUM 走 selectObjs 拿聚合值
        List<Object> sum = orderMapper.selectObjs(new QueryWrapper<Order>()
                .select("IFNULL(SUM(amount),0)")
                .eq("user_id", id)
                .in("status", paidStatus));
        vo.setTotalSpent(sum.isEmpty() ? BigDecimal.ZERO : (BigDecimal) sum.get(0));
        Inheritor inheritor = inheritorMapper.selectOne(
                new LambdaQueryWrapper<Inheritor>().eq(Inheritor::getUserId, id));
        if (inheritor != null) {
            vo.setInheritorId(inheritor.getId());
            vo.setInheritorName(inheritor.getName());
            vo.setInheritorVerifyStatus(inheritor.getVerifyStatus());
        }
        return Result.ok(vo);
    }

    /** 重置密码:生成 8 位临时密码返回一次,由管理员转交用户;旧会话全部作废 */
    @SaCheckRole("ADMIN")
    @PutMapping("/{id}/password/reset")
    public Result<String> resetPassword(@PathVariable Long id) {
        if (id.equals(StpUtil.getLoginIdAsLong())) {
            throw new BusinessException("请通过个人中心修改自己的密码");
        }
        return Result.ok(authService.adminResetPassword(id));
    }

    @SaCheckRole("ADMIN")
    @GetMapping("/inheritors")
    public Result<List<UserVO>> inheritors() {
        List<User> users = userMapper.selectList(
                new LambdaQueryWrapper<User>().eq(User::getRole, "INHERITOR"));
        return Result.ok(BeanUtil.copyToList(users, UserVO.class));
    }
}
