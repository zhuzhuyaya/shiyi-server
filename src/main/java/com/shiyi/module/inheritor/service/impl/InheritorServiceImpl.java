package com.shiyi.module.inheritor.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.shiyi.common.BusinessException;
import com.shiyi.infra.MinioService;
import com.shiyi.module.heritage.entity.HeritageItem;
import com.shiyi.module.heritage.mapper.HeritageItemMapper;
import com.shiyi.module.heritage.service.HeritageItemService;
import com.shiyi.module.inheritor.dto.InheritorApplyDTO;
import com.shiyi.module.inheritor.entity.Inheritor;
import com.shiyi.module.inheritor.mapper.InheritorMapper;
import com.shiyi.module.inheritor.service.InheritorService;
import com.shiyi.module.user.entity.User;
import com.shiyi.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InheritorServiceImpl implements InheritorService {

    private final InheritorMapper inheritorMapper;
    private final UserMapper userMapper;
    private final MinioService minioService;
    private final HeritageItemMapper heritageItemMapper;
    private final HeritageItemService heritageItemService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void apply(InheritorApplyDTO dto, List<MultipartFile> certs) {
        Long userId = StpUtil.getLoginIdAsLong();
        if (certs == null || certs.isEmpty()) {
            throw new BusinessException("请上传资质证书照片(营业执照/技艺认证等)");
        }
        Long heritageItemId = resolveHeritageItem(dto);
        Inheritor inheritor = inheritorMapper.selectOne(
                new LambdaQueryWrapper<Inheritor>().eq(Inheritor::getUserId, userId));

        // 证书先传 MinIO 拿 URL,存成 JSON 数组(同 Course.images);upload 自带类型白名单,exe 冒充图片进不来
        List<String> certUrls = certs.stream().map(minioService::upload).toList();

        if (inheritor == null) {
            inheritor = new Inheritor();
            inheritor.setUserId(userId);
            inheritor.setName(dto.getName());
            inheritor.setHeritageItemId(heritageItemId);
            inheritor.setBio(dto.getBio());
            inheritor.setContact(dto.getContact());
            inheritor.setCertImages(JSONUtil.toJsonStr(certUrls));
            inheritor.setVerifyStatus(Inheritor.VERIFY_PENDING);
            inheritorMapper.insert(inheritor);

            // 角色升级：先给 INHERITOR（能发课，但课要过审），认证结果不影响发课资格
            userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .eq(User::getId, userId)
                    .set(User::getRole, "INHERITOR"));
        } else if (Inheritor.VERIFY_REJECTED == inheritor.getVerifyStatus()) {
            // 被驳回的允许整改重提:覆盖资料与证书,状态回到待审核,旧驳回原因一并清掉
            inheritorMapper.update(null, new LambdaUpdateWrapper<Inheritor>()
                    .eq(Inheritor::getId, inheritor.getId())
                    .set(Inheritor::getName, dto.getName())
                    .set(Inheritor::getHeritageItemId, heritageItemId)
                    .set(Inheritor::getBio, dto.getBio())
                    .set(Inheritor::getContact, dto.getContact())
                    .set(Inheritor::getCertImages, JSONUtil.toJsonStr(certUrls))
                    .set(Inheritor::getVerifyStatus, Inheritor.VERIFY_PENDING)
                    .set(Inheritor::getRejectReason, null));
        } else {
            throw new BusinessException("你已提交过入驻申请");
        }
    }

    /**
     * 主攻项目二选一:选已有项目,或申请新项目(统一走 HeritageItemService.applyNew,待收录随申请审核)。
     * 新项目与入驻申请同事务,申请失败一并回滚,不会留下孤儿项目。
     */
    private Long resolveHeritageItem(InheritorApplyDTO dto) {
        String newName = dto.getNewHeritageName();
        if (newName == null || newName.isBlank()) {
            if (dto.getHeritageItemId() == null) {
                throw new BusinessException("请选择主攻非遗项目");
            }
            return dto.getHeritageItemId();
        }
        if (dto.getHeritageItemId() != null) {
            throw new BusinessException("请勿同时选择已有项目和申请新项目");
        }
        return heritageItemService.applyNew(newName, dto.getNewHeritageDesc()).getId();
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
    public void verify(Long inheritorId, boolean pass, String reason) {
        Inheritor inheritor = inheritorMapper.selectById(inheritorId);
        if (inheritor == null) {
            throw new BusinessException("传承人档案不存在");
        }
        // 用 UpdateWrapper 显式置空:通过时清掉历史驳回原因(null 字段走 updateById 是不落库的)
        String cleanReason = pass ? null
                : (reason == null || reason.isBlank() ? null : reason.trim());
        inheritorMapper.update(null, new LambdaUpdateWrapper<Inheritor>()
                .eq(Inheritor::getId, inheritorId)
                .set(Inheritor::getVerifyStatus, pass ? Inheritor.VERIFY_PASSED : Inheritor.VERIFY_REJECTED)
                .set(Inheritor::getRejectReason, cleanReason));

        // 申请时新建的项目随认证通过一并收录;驳回则保持待收录,项目真伪由管理员在字典管理里独立判断
        if (pass) {
            HeritageItem item = heritageItemMapper.selectById(inheritor.getHeritageItemId());
            if (item != null && item.getStatus() != null
                    && item.getStatus() == HeritageItem.STATUS_PENDING) {
                heritageItemMapper.update(null, new LambdaUpdateWrapper<HeritageItem>()
                        .eq(HeritageItem::getId, item.getId())
                        .set(HeritageItem::getStatus, HeritageItem.STATUS_ACTIVE));
            }
        }
    }
}
