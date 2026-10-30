package com.shiyi.module.inheritor.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.shiyi.common.BusinessException;
import com.shiyi.module.inheritor.dto.InheritorApplyDTO;
import com.shiyi.module.inheritor.entity.Inheritor;
import com.shiyi.module.inheritor.mapper.InheritorMapper;
import com.shiyi.module.inheritor.service.InheritorService;
import com.shiyi.module.user.entity.User;
import com.shiyi.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InheritorServiceImpl implements InheritorService {

    private final InheritorMapper inheritorMapper;
    private final UserMapper userMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void apply(InheritorApplyDTO dto) {
        Long userId = StpUtil.getLoginIdAsLong();
        Long exists = inheritorMapper.selectCount(
                new LambdaQueryWrapper<Inheritor>().eq(Inheritor::getUserId, userId));
        if (exists > 0) {
            throw new BusinessException("你已提交过入驻申请");
        }
        Inheritor inheritor = new Inheritor();
        inheritor.setUserId(userId);
        inheritor.setName(dto.getName());
        inheritor.setHeritageItemId(dto.getHeritageItemId());
        inheritor.setBio(dto.getBio());
        inheritor.setContact(dto.getContact());
        inheritor.setVerifyStatus(Inheritor.VERIFY_PENDING);
        inheritorMapper.insert(inheritor);

        // 角色升级：先给 INHERITOR（能发课，但课要过审），认证结果不影响发课资格
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getRole, "INHERITOR"));
    }

    @Override
    public Inheritor currentInheritor() {
        Long userId = StpUtil.getLoginIdAsLong();
        Inheritor inheritor = inheritorMapper.selectOne(
                new LambdaQueryWrapper<Inheritor>().eq(Inheritor::getUserId, userId));
        if (inheritor == null) {
            throw new BusinessException("尚未申请传承人入驻");
        }
        return inheritor;
    }

    @Override
    public List<Inheritor> listByStatus(Integer status) {
        return inheritorMapper.selectList(
                new LambdaQueryWrapper<Inheritor>().eq(Inheritor::getVerifyStatus, status));
    }

    @Override
    public void verify(Long inheritorId, boolean pass) {
        Inheritor inheritor = inheritorMapper.selectById(inheritorId);
        if (inheritor == null) {
            throw new BusinessException("传承人档案不存在");
        }
        inheritor.setVerifyStatus(pass ? Inheritor.VERIFY_PASSED : Inheritor.VERIFY_REJECTED);
        inheritorMapper.updateById(inheritor);
    }
}
