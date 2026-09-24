package com.qjj.user.framework.idempotent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qjj.user.common.constants.CommonConstants;
import com.qjj.user.common.enums.UserErrorCodeEnum;
import com.qjj.user.common.exception.BizException;
import com.qjj.user.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

/**
 * @Idempotent AOP：Redis SET NX 占位成功才执行；重复请求返回首次 Result（禁止裸抛/笼统失败码）。
 * 首次结果以 JSON 占位存储；执行中撞键返回「处理中」业务码。
 */
@Slf4j
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
            log.warn("幂等键缺失（Idempotent-Key / X-Request-Id 均无），跳过幂等: {}", pjp.getSignature());
            return pjp.proceed();
        }
        String redisKey = "user:idempotent:" + idempotent.keyPrefix() + ":" + key;
        Duration ttl = Duration.ofSeconds(idempotent.ttlSeconds());
        Boolean first = stringRedisTemplate.opsForValue().setIfAbsent(redisKey, RUNNING, ttl);
        if (!Boolean.TRUE.equals(first)) {
            String stored = stringRedisTemplate.opsForValue().get(redisKey);
            if (stored == null) {
                // 占位恰好过期，重新占位执行
                Boolean retry = stringRedisTemplate.opsForValue().setIfAbsent(redisKey, RUNNING, ttl);
                if (!Boolean.TRUE.equals(retry)) {
                    throw new BizException(UserErrorCodeEnum.DUPLICATE_REQUEST_IN_PROGRESS);
                }
            } else if (RUNNING.equals(stored)) {
                throw new BizException(UserErrorCodeEnum.DUPLICATE_REQUEST_IN_PROGRESS);
            } else {
                Type returnType = ((MethodSignature) pjp.getSignature()).getMethod().getGenericReturnType();
                return objectMapper.readValue(stored, objectMapper.getTypeFactory().constructType(returnType));
            }
        }
        try {
            Object result = pjp.proceed();
            if (result instanceof Result<?> r && r.getCode() == 0) {
                stringRedisTemplate.opsForValue().set(redisKey, objectMapper.writeValueAsString(r), ttl);
            } else {
                // 失败不保留占位，允许客户端修正后重试
                stringRedisTemplate.delete(redisKey);
            }
            return result;
        } catch (Throwable ex) {
            stringRedisTemplate.delete(redisKey);
            throw ex;
        }
    }

    private String resolveKey() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }
        HttpServletRequest request = attrs.getRequest();
        String key = request.getHeader(CommonConstants.HEADER_IDEMPOTENT_KEY);
        if (key == null || key.isBlank()) {
            key = request.getHeader(CommonConstants.HEADER_REQUEST_ID);
        }
        return key;
    }
}
