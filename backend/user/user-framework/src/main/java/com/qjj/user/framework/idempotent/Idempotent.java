package com.qjj.user.framework.idempotent;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 写幂等（CLAUDE.md 6.11）：客户端携带 Idempotent-Key（无则 X-Request-Id）；
 * Redis SET NX 占位成功才执行业务；重复请求返回首次 Result。
 * 禁止业务自写 setnx / 仅靠前端防重 / 用 uk_ 做幂等。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

    /** 幂等键前缀（区分接口，最终键：user:idempotent:{prefix}:{key}） */
    String keyPrefix();

    /** 占位与结果保留时长（秒，业务可覆盖 TTL） */
    long ttlSeconds() default 3600L;
}
