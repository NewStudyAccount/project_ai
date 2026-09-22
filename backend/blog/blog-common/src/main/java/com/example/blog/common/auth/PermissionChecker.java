package com.example.blog.common.auth;

import java.util.Set;

/** 本地 RBAC 鉴权语义。 */
public interface PermissionChecker {

    Set<String> permissionCodes(long userId);

    default boolean hasPermission(long userId, String permissionCode) {
        return permissionCodes(userId).contains(permissionCode);
    }
}
