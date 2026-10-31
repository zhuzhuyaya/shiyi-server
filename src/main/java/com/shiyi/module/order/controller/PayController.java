package com.shiyi.module.order.controller;

import com.shiyi.module.order.service.AlipayService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/pay")
@RequiredArgsConstructor
public class PayController {

    private final AlipayService alipayService;

    /**
     * 异步回调:支付宝服务器 POST 表单过来,无登录态(SaToken 已加白名单)。
     * 返回值必须是纯文本 success/failure,绝不能套 Result 包装,
     * 也不能被全局异常处理器接走——所以方法体自己兜异常
     */
    @PostMapping(value = "/notify", produces = "text/plain;charset=UTF-8")
    public String notify(@RequestParam Map<String, String> params) {
        try {
            return alipayService.handleNotify(params);
        } catch (Exception e) {
            return "failure";   // 让支付宝重发,等我们恢复
        }
    }
}
