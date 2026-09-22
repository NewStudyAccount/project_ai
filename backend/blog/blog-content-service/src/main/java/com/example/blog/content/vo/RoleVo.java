package com.example.blog.content.vo;

/** 角色出参。 */
public record RoleVo(
        Long id,
        String systemCode,
        String roleCode,
        String roleName,
        Integer sort,
        Integer status,
        String remark
) {
}
