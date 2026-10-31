package com.shiyi.module.heritage.controller;

import com.shiyi.common.Result;
import com.shiyi.module.heritage.entity.HeritageItem;

import com.shiyi.module.heritage.service.HeritageItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/heritage-items")
@RequiredArgsConstructor
public class HeritageItemController {

    private final HeritageItemService heritageItemService;

    /** 字典下拉:GET /api/v1/heritage-items,全部非遗项目 */
    @GetMapping
    public Result<List<HeritageItem>> list() {
        return Result.ok(heritageItemService.list());
    }
}
