package com.example.blog.content.dto;

import jakarta.validation.constraints.NotBlank;

/** 创建角色入参。 */
public record RoleCreateRequest(
        @NotBlank String roleCode,
        String roleName,
        String remark
) {
}
