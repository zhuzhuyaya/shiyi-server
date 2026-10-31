package com.shiyi.infra.limiter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 接口限流:按登录用户 + 滑动窗口计数,超限抛业务异常 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    String key();              // 业务标识,拼进 Redis key

    int window() default 1;    // 窗口:秒

    int max() default 5;       // 窗口内最大次数
}
