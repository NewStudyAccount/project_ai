package com.example.blog.content.dto;

import jakarta.validation.constraints.NotNull;

/** 用户-角色赋权入参。 */
public record UserRoleRequest(
        @NotNull Long userId,
        @NotNull Long roleId
) {
}
