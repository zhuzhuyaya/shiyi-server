package com.shiyi.infra.limiter;

import cn.dev33.satoken.stp.StpUtil;
import com.shiyi.common.BusinessException;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    /**
     * 滑动窗口 Lua:zset 成员=请求时间戳,分数=时间戳
     * 先删窗口外的旧记录,再数窗口内次数,未超限则记录本次并续期
     */
    private static final String LUA = """
            redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, tonumber(ARGV[1]) - tonumber(ARGV[2]))
            if redis.call('ZCARD', KEYS[1]) >= tonumber(ARGV[3]) then
                return 0
            end
            redis.call('ZADD', KEYS[1], ARGV[1], ARGV[4])
            redis.call('PEXPIRE', KEYS[1], ARGV[2])
            return 1
            """;
    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>(LUA, Long.class);

    private final StringRedisTemplate stringRedisTemplate;

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint pjp, RateLimit rateLimit) throws Throwable {
        long userId = StpUtil.getLoginIdAsLong();
        String key = "ratelimit:" + rateLimit.key() + ":" + userId;
        Long pass = stringRedisTemplate.execute(SCRIPT,
                List.of(key),
                String.valueOf(System.currentTimeMillis()),
                String.valueOf(rateLimit.window() * 1000L),
                String.valueOf(rateLimit.max()),
                UUID.randomUUID().toString());
        if (pass == null || pass == 0) {
            throw new BusinessException("操作太频繁,请稍后再试");
        }
        return pjp.proceed();
    }
}
