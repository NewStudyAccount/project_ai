package com.example.blog.common.auth;

import com.example.blog.common.audit.CurrentUser;
import com.example.blog.common.error.BizException;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/** 无权限抛 403 语义业务异常。 */
@Aspect
@Component
@ConditionalOnBean(PermissionChecker.class)
public class PermissionAspect {

    private final PermissionChecker permissionChecker;

    public PermissionAspect(PermissionChecker permissionChecker) {
        this.permissionChecker = permissionChecker;
    }

    @Before("@annotation(requiresPermission)")
    public void check(RequiresPermission requiresPermission) {
        Long uid = CurrentUser.idOrNull();
        if (uid == null) {
            throw BizException.unauthorized();
        }
        if (!permissionChecker.hasPermission(uid, requiresPermission.code())) {
            throw BizException.forbidden();
        }
    }
}
