package com.qjj.auth.framework.idempotent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qjj.auth.common.constants.CommonConstants;
import com.qjj.auth.common.enums.AuthErrorCodeEnum;
import com.qjj.auth.common.exception.BizException;
import com.qjj.auth.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Type;
import java.time.Duration;

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
@RequiredArgsConstructor
public class IdempotentAspect {
    private static final String RUNNING = "__RUNNING__";
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint pjp, Idempotent idempotent) throws Throwable {
        String key = resolveKey();
        if (key == null || key.isBlank()) {
            return pjp.proceed();
        }
        String redisKey = "auth:idempotent:" + idempotent.keyPrefix() + ":" + key;
        Duration ttl = Duration.ofSeconds(idempotent.ttlSeconds());
        Boolean first = stringRedisTemplate.opsForValue().setIfAbsent(redisKey, RUNNING, ttl);
        if (!Boolean.TRUE.equals(first)) {
            String stored = stringRedisTemplate.opsForValue().get(redisKey);
            if (stored != null && !RUNNING.equals(stored)) {
                Type returnType = ((MethodSignature) pjp.getSignature()).getMethod().getGenericReturnType();
                return objectMapper.readValue(stored, objectMapper.getTypeFactory().constructType(returnType));
            }
            throw new BizException(AuthErrorCodeEnum.DUPLICATE_REQUEST_IN_PROGRESS);
        }
        try {
            Object result = pjp.proceed();
            if (result instanceof Result<?> r && r.getCode() == 0) {
                stringRedisTemplate.opsForValue().set(redisKey, objectMapper.writeValueAsString(r), ttl);
            } else {
                stringRedisTemplate.delete(redisKey);
            }
            return result;
        } catch (Throwable ex) {
            stringRedisTemplate.delete(redisKey);
            throw ex;
        }
    }

    private String resolveKey() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes servletAttrs)) {
            return null;
        }
        HttpServletRequest request = servletAttrs.getRequest();
        String key = request.getHeader(CommonConstants.HEADER_IDEMPOTENT_KEY);
        return key == null || key.isBlank() ? request.getHeader(CommonConstants.HEADER_REQUEST_ID) : key;
    }
}
