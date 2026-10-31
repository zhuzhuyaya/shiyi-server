package com.shiyi.module.order.pay;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 支付宝沙盒配置,对应 application.yaml 的 alipay 前缀 */
@Data
@Component
@ConfigurationProperties(prefix = "alipay")
public class AlipayProperties {

    /** 沙箱应用 APPID(9021000 开头) */
    private String appId;

    /** 沙箱网关,以沙箱控制台显示为准 */
    private String gateway;

    /** 应用私钥(自己用密钥工具生成的 RSA2) */
    private String merchantPrivateKey;

    /** 支付宝公钥(控制台给的,不是自己生成的应用公钥) */
    private String alipayPublicKey;

    /** 异步回调地址:支付宝服务器调用,必须公网可达,本地开发靠主动查单兜底 */
    private String notifyUrl;

    /** 付款完成浏览器跳回地址 */
    private String returnUrl;
}
