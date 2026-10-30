package com.shiyi.module.course.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.shiyi.common.Result;
import com.shiyi.module.inheritor.dto.InheritorApplyDTO;
import com.shiyi.module.inheritor.entity.Inheritor;
import com.shiyi.module.inheritor.service.InheritorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inheritor")
@RequiredArgsConstructor
public class InheritorController {

    private final InheritorService inheritorService;

    @PostMapping("/apply")
    public Result<Void> apply(@Valid @RequestBody InheritorApplyDTO dto) {
        inheritorService.apply(dto);
        return Result.ok();
    }

    @SaCheckRole("INHERITOR")
    @GetMapping("/me")
    public Result<Inheritor> me() {
        return Result.ok(inheritorService.currentInheritor());
    }
}
