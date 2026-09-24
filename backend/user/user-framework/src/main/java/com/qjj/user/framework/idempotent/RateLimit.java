package com.qjj.user.framework.idempotent;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流（CLAUDE.md 6.11）：底层 Redisson RRateLimiter；超限 HTTP 429 + 系统码 10003。
 * 禁止 Sentinel / Gateway RequestRateLimiter 作限流主路径、禁止 Redis+Lua 自研。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /** 限流键前缀（最终键：user:ratelimit:{prefix}:{维度值}） */
    String keyPrefix();

    /** 限流维度 */
    Dimension dimension() default Dimension.USER;

    /** 窗口内允许次数 */
    long permits() default 20;

    /** 窗口时长（秒） */
    long windowSeconds() default 60L;

    enum Dimension {
        /** 按登录用户（X-User-Id），未登录退化为 IP */
        USER,
        /** 按来源 IP */
        IP,
        /** 按接口（全局共享额度） */
        INTERFACE
    }
}
