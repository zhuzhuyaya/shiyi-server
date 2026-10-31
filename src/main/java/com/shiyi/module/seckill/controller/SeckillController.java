package com.shiyi.module.seckill.controller;

import com.shiyi.common.Result;
import com.shiyi.infra.limiter.RateLimit;
import com.shiyi.module.seckill.service.SeckillService;
import com.shiyi.module.seckill.vo.SeckillResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/seckill")
@RequiredArgsConstructor
public class SeckillController {

    private final SeckillService seckillService;

    /** 抢课:POST /api/v1/seckill/2 。限流:同一用户每秒最多 4 次 */
    @RateLimit(key = "seckill", window = 1, max = 4)
    @PostMapping("/{sessionId}")
    public Result<Void> grab(@PathVariable Long sessionId) {
        seckillService.grab(sessionId);
        return Result.ok();   // 正常返回 = 已进入排队,结果异步
    }

    /** 抢课结果轮询:GET /api/v1/seckill/2/result,SUCCESS 时带 orderId 供前端跳转 */
    @GetMapping("/{sessionId}/result")
    public Result<SeckillResultVO> result(@PathVariable Long sessionId) {
        return Result.ok(seckillService.result(sessionId));
    }
}
