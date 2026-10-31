package com.shiyi.module.order.pay;

import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class AlipayConfig {

    private final AlipayProperties props;

    @Bean
    public AlipayClient alipayClient() {
        // 参数顺序:网关、appId、应用私钥、响应格式、编码、支付宝公钥、签名类型
        return new DefaultAlipayClient(
                props.getGateway(), props.getAppId(),
                props.getMerchantPrivateKey(), "json", "UTF-8",
                props.getAlipayPublicKey(), "RSA2");
    }
}
