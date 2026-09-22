package com.example.auth.user.dto;

import com.example.auth.common.Result;

/**
 * 用户中心响应包装（与业务系统统一 Result 对齐）。
 */
public record UserCenterResponse<T>(Result<T> result) {

    public static <T> UserCenterResponse<T> of(Result<T> result) {
        return new UserCenterResponse<>(result);
    }
}
