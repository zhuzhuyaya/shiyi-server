package com.shiyi.module.heritage.controller;

import com.shiyi.common.Result;
import com.shiyi.module.heritage.entity.HeritageItem;
import com.shiyi.module.heritage.service.HeritageItemService;
import cn.dev33.satoken.annotation.SaCheckRole;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 非遗字典管理:项目不再只能运维手插库,用户申请的新项目也在这里审核收录 */
@RestController
@RequestMapping("/api/v1/admin/heritage-items")
@RequiredArgsConstructor
@SaCheckRole("ADMIN")
public class HeritageAdminController {

    private final HeritageItemService heritageItemService;

    @GetMapping
    public Result<List<HeritageItem>> list(@RequestParam(required = false) Integer status,
                                           @RequestParam(required = false) String keyword) {
        return Result.ok(heritageItemService.listAll(status, keyword));
    }

    @PostMapping
    public Result<HeritageItem> create(@RequestBody HeritageItem item) {
        return Result.ok(heritageItemService.create(item));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody HeritageItem item) {
        heritageItemService.update(id, item);
        return Result.ok();
    }

    /** true=收录/恢复,false=拒绝收录/停用 */
    @PutMapping("/{id}/status")
    public Result<Void> setStatus(@PathVariable Long id, @RequestParam boolean enabled) {
        heritageItemService.setStatus(id, enabled);
        return Result.ok();
    }
}
