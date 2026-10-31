package com.shiyi.module.course.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.shiyi.common.Result;
import com.shiyi.infra.limiter.RateLimit;
import com.shiyi.module.heritage.dto.HeritageApplyDTO;
import com.shiyi.module.heritage.service.HeritageItemService;
import com.shiyi.module.inheritor.dto.InheritorApplyDTO;
import com.shiyi.module.inheritor.entity.Inheritor;
import com.shiyi.module.inheritor.service.InheritorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/inheritor")
@RequiredArgsConstructor
public class InheritorController {

    private final InheritorService inheritorService;
    private final HeritageItemService heritageItemService;

    /** 入驻申请:multipart 表单(资料字段 + 资质证书照片),证书走 MinIO */
    @PostMapping(value = "/apply", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Void> apply(@Valid InheritorApplyDTO dto,
                              @RequestParam(value = "certs", required = false) List<MultipartFile> certs) {
        inheritorService.apply(dto, certs);
        return Result.ok();
    }

    @SaCheckRole("INHERITOR")
    @GetMapping("/me")
    public Result<Inheritor> me() {
        return Result.ok(inheritorService.currentInheritor());
    }

    /** 已入驻的传承人也能申请收录新项目,与入驻申请共用管理端待收录队列 */
    @SaCheckRole("INHERITOR")
    @RateLimit(key = "heritage:apply", window = 60, max = 5)
    @PostMapping("/heritage-items")
    public Result<Void> applyHeritage(@Valid @RequestBody HeritageApplyDTO dto) {
        heritageItemService.applyNew(dto.getName(), dto.getDescription());
        return Result.ok();
    }
}
