package com.qjj.user.framework.idempotent;

import com.qjj.user.common.enums.SysErrorCodeEnum;
import com.qjj.user.common.exception.BizException;
import com.qjj.user.framework.core.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.ProceedingJoinPoint;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * @RateLimit AOP：Redisson RRateLimiter（CLAUDE.md 6.11）；超限抛业务异常由全局处理映射 HTTP 429 + 10003。
 */
@Slf4j
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 110)
@RequiredArgsConstructor
public class RateLimitAspect {

    private final RedissonClient redissonClient;

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint pjp, RateLimit rateLimit) throws Throwable {
        String dimValue = dimensionValue(rateLimit.dimension());
        String name = "user:ratelimit:" + rateLimit.keyPrefix() + ":" + dimValue;
        RRateLimiter limiter = redissonClient.getRateLimiter(name);
        limiter.trySetRate(RateType.OVERALL, rateLimit.permits(), rateLimit.windowSeconds(), RateIntervalUnit.SECONDS);
        if (!limiter.tryAcquire()) {
            log.warn("接口限流触发: key={}, dim={}", rateLimit.keyPrefix(), dimValue);
            throw new BizException(SysErrorCodeEnum.RATE_LIMITED);
        }
        return pjp.proceed();
    }

    private String dimensionValue(RateLimit.Dimension dimension) {
        return switch (dimension) {
            case INTERFACE -> "all";
            case IP -> resolveIp();
            case USER -> {
                Long userId = UserContext.get() == null ? null : UserContext.get().getUserId();
                yield userId != null ? String.valueOf(userId) : resolveIp();
            }
        };
    }

    private String resolveIp() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return "unknown";
        }
        HttpServletRequest request = attrs.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
