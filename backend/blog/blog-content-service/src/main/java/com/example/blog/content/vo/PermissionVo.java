package com.example.blog.content.vo;

/** 权限出参。 */
public record PermissionVo(
        Long id,
        String systemCode,
        Long parentId,
        Integer permissionType,
        String permissionCode,
        String permissionName,
        Integer sort,
        Integer status
) {
}
