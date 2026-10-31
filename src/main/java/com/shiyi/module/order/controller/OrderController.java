package com.shiyi.module.order.controller;

import com.shiyi.common.Result;
import com.shiyi.module.order.service.AlipayService;
import com.shiyi.module.order.service.OrderService;
import com.shiyi.module.order.vo.OrderVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final AlipayService alipayService;

    /** 我的订单:GET /api/v1/orders */
    @GetMapping
    public Result<List<OrderVO>> my() {
        return Result.ok(orderService.listMine());
    }

    /** 模拟支付:POST /api/v1/orders/1/pay */
    @PostMapping("/{orderId}/pay")
    public Result<Void> pay(@PathVariable Long orderId) {
        orderService.pay(orderId);
        return Result.ok();
    }

    @PostMapping("/{orderId}/refund")
    public Result<Void> refund(@PathVariable Long orderId) {
        orderService.refund(orderId);
        return Result.ok();
    }

    @GetMapping("/{orderId}")
    public Result<OrderVO> detail(@PathVariable Long orderId) {
        return Result.ok(orderService.detail(orderId));
    }

    /** 发起支付宝支付:返回沙箱收银台跳转地址(GET 模式) */
    @GetMapping("/{orderId}/alipay")
    public Result<String> alipay(@PathVariable Long orderId) {
        return Result.ok(alipayService.createPay(orderId));
    }

    /** 主动查单同步:付款回来后前端调用,回调不可达时的兜底 */
    @GetMapping("/{orderId}/alipay-query")
    public Result<String> alipayQuery(@PathVariable Long orderId) {
        return Result.ok(alipayService.queryAndSync(orderId));
    }

}