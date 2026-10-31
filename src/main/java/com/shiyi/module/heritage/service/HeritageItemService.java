package com.shiyi.module.heritage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.shiyi.common.BusinessException;
import com.shiyi.module.heritage.entity.HeritageItem;
import com.shiyi.module.heritage.mapper.HeritageItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class HeritageItemService {

    private final HeritageItemMapper heritageItemMapper;

    /** 字典下拉:GET /api/v1/heritage-items,只暴露已收录的项目(待收录/已停用对外不可见) */
    public List<HeritageItem> list() {
        return heritageItemMapper.selectList(new LambdaQueryWrapper<HeritageItem>()
                .eq(HeritageItem::getStatus, HeritageItem.STATUS_ACTIVE)
                .orderByAsc(HeritageItem::getId));
    }

    /** 管理端字典列表:可按状态、名称过滤 */
    public List<HeritageItem> listAll(Integer status, String keyword) {
        LambdaQueryWrapper<HeritageItem> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(HeritageItem::getStatus, status);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(HeritageItem::getName, keyword.trim());
        }
        return heritageItemMapper.selectList(wrapper.orderByDesc(HeritageItem::getId));
    }

    public HeritageItem create(HeritageItem item) {
        validate(item);
        checkNameUnique(item.getName(), null);
        item.setId(null);
        item.setStatus(HeritageItem.STATUS_ACTIVE);
        if (item.getCover() == null) {
            item.setCover("");
        }
        heritageItemMapper.insert(item);
        return item;
    }

    public void update(Long id, HeritageItem dto) {
        if (heritageItemMapper.selectById(id) == null) {
            throw new BusinessException("非遗项目不存在");
        }
        validate(dto);
        checkNameUnique(dto.getName(), id);
        heritageItemMapper.update(null, new LambdaUpdateWrapper<HeritageItem>()
                .eq(HeritageItem::getId, id)
                .set(HeritageItem::getName, dto.getName())
                .set(HeritageItem::getCategory, dto.getCategory())
                .set(HeritageItem::getLevel, dto.getLevel())
                .set(HeritageItem::getRegion, dto.getRegion())
                .set(HeritageItem::getDescription, dto.getDescription()));
    }

    /** 收录/拒绝收录/停用/恢复统一走这里:true=已收录,false=已停用 */
    public void setStatus(Long id, boolean enabled) {
        if (heritageItemMapper.selectById(id) == null) {
            throw new BusinessException("非遗项目不存在");
        }
        heritageItemMapper.update(null, new LambdaUpdateWrapper<HeritageItem>()
                .eq(HeritageItem::getId, id)
                .set(HeritageItem::getStatus, enabled ? HeritageItem.STATUS_ACTIVE : HeritageItem.STATUS_DISABLED));
    }

    /** 入驻申请查重用:任意状态下同名即算重复(含别人还没审完的申请) */
    public HeritageItem findByName(String name) {
        return heritageItemMapper.selectOne(new LambdaQueryWrapper<HeritageItem>()
                .eq(HeritageItem::getName, name)
                .last("LIMIT 1"));
    }

    /** 用户提交新项目:同名查重后以待收录入库,门类/级别占位,管理员审核时在字典管理里补全 */
    public HeritageItem applyNew(String newName, String desc) {
        String name = newName == null ? "" : newName.trim();
        if (name.isEmpty()) {
            throw new BusinessException("项目名称不能为空");
        }
        if (findByName(name) != null) {
            throw new BusinessException("已存在同名非遗项目（或同名申请审核中），请直接选择");
        }
        HeritageItem item = new HeritageItem();
        item.setName(name);
        item.setCategory("其他");
        item.setLevel("待定");
        item.setRegion("");
        item.setCover("");
        item.setDescription(desc);
        item.setStatus(HeritageItem.STATUS_PENDING);
        heritageItemMapper.insert(item);
        return item;
    }

    private void validate(HeritageItem item) {
        if (item.getName() == null || item.getName().isBlank()) {
            throw new BusinessException("项目名称不能为空");
        }
        if (item.getCategory() == null || item.getCategory().isBlank()) {
            throw new BusinessException("门类不能为空");
        }
        if (item.getLevel() == null || item.getLevel().isBlank()) {
            throw new BusinessException("级别不能为空");
        }
    }

    private void checkNameUnique(String name, Long excludeId) {
        LambdaQueryWrapper<HeritageItem> wrapper = new LambdaQueryWrapper<HeritageItem>()
                .eq(HeritageItem::getName, name.trim());
        if (excludeId != null) {
            wrapper.ne(HeritageItem::getId, excludeId);
        }
        if (heritageItemMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("已存在同名非遗项目");
        }
    }
}
