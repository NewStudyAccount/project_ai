package com.example.auth.common;

/**
 * 通用枚举契约：库中存 code，接口只传 code。
 */
public interface BaseEnum<T> {

    T getCode();

    String getDesc();
}
