package com.qjj.auth.framework.idempotent;

import com.qjj.auth.common.enums.SysErrorCodeEnum;
import com.qjj.auth.common.exception.BizException;
import com.qjj.auth.framework.core.AuthContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 110)
@RequiredArgsConstructor
public class RateLimitAspect {
    private final RedissonClient redissonClient;

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint pjp, RateLimit rateLimit) throws Throwable {
        String dimValue = dimensionValue(rateLimit.dimension());
        RRateLimiter limiter = redissonClient.getRateLimiter("auth:ratelimit:" + rateLimit.keyPrefix() + ":" + dimValue);
        limiter.trySetRate(RateType.OVERALL, rateLimit.permits(), rateLimit.windowSeconds(), RateIntervalUnit.SECONDS);
        if (!limiter.tryAcquire()) {
            throw new BizException(SysErrorCodeEnum.RATE_LIMITED);
        }
        return pjp.proceed();
    }

    private String dimensionValue(RateLimit.Dimension dimension) {
        return switch (dimension) {
            case INTERFACE -> "all";
            case IP -> resolveIp();
            case USER -> AuthContext.userIdOrSystem() == 0L ? resolveIp() : String.valueOf(AuthContext.userIdOrSystem());
        };
    }

    private String resolveIp() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes servletAttrs)) {
            return "unknown";
        }
        HttpServletRequest request = servletAttrs.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
    }
}
