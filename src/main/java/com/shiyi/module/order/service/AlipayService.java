package com.shiyi.module.order.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.json.JSONObject;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shiyi.common.BusinessException;
import com.shiyi.module.order.entity.Order;
import com.shiyi.module.order.mapper.OrderMapper;
import com.shiyi.module.order.pay.AlipayProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlipayService {

    private final AlipayClient alipayClient;
    private final AlipayProperties props;
    private final OrderMapper orderMapper;
    private final OrderService orderService;

    /** 创建支付:PC 网站支付,GET 模式返回收银台跳转地址 */
    public String createPay(Long orderId) {
        long userId = StpUtil.getLoginIdAsLong();
        Order order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException("订单不存在");
        }
        if (order.getStatus() != Order.STATUS_PENDING_PAY) {
            throw new BusinessException("订单当前不可支付");
        }

        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        request.setNotifyUrl(props.getNotifyUrl());
        request.setReturnUrl(props.getReturnUrl());
        // 业务参数只放订单号和金额——金额取 DB 快照,前端传的一律不信
        JSONObject biz = new JSONObject();
        biz.set("out_trade_no", order.getOrderNo());
        biz.set("total_amount", order.getAmount().toPlainString());
        biz.set("subject", "拾遗体验课订单 " + order.getOrderNo());
        biz.set("product_code", "FAST_INSTANT_TRADE_PAY");
        request.setBizContent(biz.toString());

        try {
            // 第二个参数 "GET":响应体是完整跳转 URL;默认 POST 模式给的是自动提交表单,SPA 里不好接
            AlipayTradePagePayResponse resp = alipayClient.pageExecute(request, "GET");
            return resp.getBody();
        } catch (AlipayApiException e) {
            throw new BusinessException("创建支付失败：" + e.getMessage());
        }
    }

    /**
     * 支付宝异步回调。三点必须记住:
     * 1) 调用方是支付宝服务器,无登录态,不返回 Result 包装——协议要求纯文本 "success"/"failure"
     * 2) 答非 "success" 支付宝会重发(间隔 4m/10m/10m/1h... 共 8 次),业务异常也要答 success 防重发风暴
     * 3) 验签防伪造,金额校验防错单,条件更新防并发——三道防线各管一头
     */
    public String handleNotify(Map<String, String> params) {
        boolean signVerified;
        try {
            signVerified = AlipaySignature.rsaCheckV1(params, props.getAlipayPublicKey(), "UTF-8", "RSA2");
        } catch (AlipayApiException e) {
            signVerified = false;
        }
        if (!signVerified) {
            log.warn("支付宝回调验签失败: {}", params);
            return "failure";
        }

        String tradeStatus = params.get("trade_status");
        if (!"TRADE_SUCCESS".equals(tradeStatus) && !"TRADE_FINISHED".equals(tradeStatus)) {
            return "success";   // 非成功状态(如等待付款),确认收到即可
        }

        Order order = orderMapper.selectOne(Wrappers.<Order>lambdaQuery()
                .eq(Order::getOrderNo, params.get("out_trade_no")));
        if (order == null) {
            log.warn("回调找不到订单 out_trade_no={}", params.get("out_trade_no"));
            return "success";   // 陌生单号,确认防重发,人工排查
        }
        // 金额校验:以 DB 快照为准,防"付 1 分钱标记 88 元订单"
        BigDecimal paid = new BigDecimal(params.get("total_amount"));
        if (order.getAmount().compareTo(paid) != 0) {
            log.error("订单 {} 回调金额不一致,DB={},支付宝={}", order.getOrderNo(), order.getAmount(), paid);
            return "success";   // 不改状态但确认,人工处理
        }

        try {
            if (order.getStatus() == Order.STATUS_PENDING_PAY) {
                orderService.markPaid(order);   // 复用状态机唯一口
            }
        } catch (BusinessException e) {
            log.info("回调幂等退出: {}", e.getMessage());   // 并发下已被处理,仍答 success
        }
        return "success";
    }

    /** 主动查单:回调不可达(本地开发/网络抖动)时的兜底,前端付款回来后调用 */
    public String queryAndSync(Long orderId) {
        long userId = StpUtil.getLoginIdAsLong();
        Order order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException("订单不存在");
        }
        if (order.getStatus() != Order.STATUS_PENDING_PAY) {
            return "PAID";
        }
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        JSONObject biz = new JSONObject();
        biz.set("out_trade_no", order.getOrderNo());
        request.setBizContent(biz.toString());
        try {
            AlipayTradeQueryResponse resp = alipayClient.execute(request);
            if (resp.isSuccess() && ("TRADE_SUCCESS".equals(resp.getTradeStatus())
                    || "TRADE_FINISHED".equals(resp.getTradeStatus()))) {
                orderService.markPaid(order);
                return "PAID";
            }
            return resp.getTradeStatus() == null ? "WAIT_BUYER_PAY" : resp.getTradeStatus();
        } catch (AlipayApiException e) {
            throw new BusinessException("查单失败：" + e.getMessage());
        }
    }
}
