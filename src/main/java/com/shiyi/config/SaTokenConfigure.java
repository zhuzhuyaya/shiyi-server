package com.shiyi.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SaTokenConfigure implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkLogin()))
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/v1/courses/**")
                .excludePathPatterns("/api/v1/heritage-items/**")
                .excludePathPatterns("/api/v1/auth/login", "/api/v1/auth/register")
                .excludePathPatterns("/api/v1/rag/ask")   // 仅问答匿名可用;索引重建已收敛到 /admin/rag/reindex(ADMIN)
                .excludePathPatterns("/api/v1/pay/notify");   // 支付宝服务器回调,无登录态(第 12 课)
    }
}
