package com.shiyi.module.order.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.shiyi.common.Result;
import com.shiyi.module.order.dto.CheckinDTO;
import com.shiyi.module.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inheritor/checkin")
@RequiredArgsConstructor
@SaCheckRole("INHERITOR")
public class InheritorCheckinController {

    private final OrderService orderService;

    /** 到店核销:POST /api/v1/inheritor/checkin  body: {"code":"xxxxxxxx"} */
    @PostMapping
    public Result<Void> checkin(@Valid @RequestBody CheckinDTO dto) {
        orderService.checkin(dto.getCode());
        return Result.ok();
    }
}
