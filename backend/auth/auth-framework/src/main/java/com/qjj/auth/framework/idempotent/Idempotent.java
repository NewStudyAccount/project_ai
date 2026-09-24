package com.qjj.auth.framework.idempotent;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {
    String keyPrefix();
    long ttlSeconds() default 3600L;
}
