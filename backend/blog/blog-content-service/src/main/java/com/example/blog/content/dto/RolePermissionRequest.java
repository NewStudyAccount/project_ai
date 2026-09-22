package com.example.blog.content.dto;

import jakarta.validation.constraints.NotNull;

/** 角色-权限绑定入参。 */
public record RolePermissionRequest(
        @NotNull Long roleId,
        @NotNull Long permissionId
) {
}
