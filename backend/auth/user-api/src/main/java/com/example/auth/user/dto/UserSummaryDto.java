package com.example.auth.user.dto;

/**
 * 用户中心账号摘要（登录解析用）。Long 在出参侧已全局转 String。
 */
public record UserSummaryDto(
        Long id,
        String username,
        Integer status,
        String realName,
        String nickname
) {
}
