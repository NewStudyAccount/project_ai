package com.qjj.auth.framework.idempotent;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {
    String keyPrefix();
    Dimension dimension() default Dimension.USER;
    long permits() default 20;
    long windowSeconds() default 60L;

    enum Dimension {
        USER, IP, INTERFACE
    }
}
