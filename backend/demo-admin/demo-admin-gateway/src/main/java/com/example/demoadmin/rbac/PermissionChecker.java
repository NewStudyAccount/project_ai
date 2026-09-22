package com.example.demoadmin.rbac;

import java.util.Set;

/**
 * 本地 RBAC 鉴权语义：JWT.uid + 本系统 user_role/role_permission。
 * 实体表见 deploy/db/migration/system_db/V2__system_rbac_foundation.sql。
 */
public interface PermissionChecker {

    /** 当前用户在本系统的权限码集合。 */
    Set<String> permissionCodes(long userId, String systemCode);

    default boolean hasPermission(long userId, String systemCode, String permissionCode) {
        return permissionCodes(userId, systemCode).contains(permissionCode);
    }
}
