package com.example.blog.content.controller;

import com.example.blog.common.auth.BlogPermissions;
import com.example.blog.common.auth.RequiresPermission;
import com.example.blog.common.page.PageQuery;
import com.example.blog.common.result.PageResult;
import com.example.blog.common.result.Result;
import com.example.blog.content.dto.RoleCreateRequest;
import com.example.blog.content.dto.RolePermissionRequest;
import com.example.blog.content.dto.UserRoleRequest;
import com.example.blog.content.entity.SysPermission;
import com.example.blog.content.entity.SysRole;
import com.example.blog.content.service.RbacService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** RBAC 管理接口（system_code=blog）。 */
@RestController
@RequestMapping("/api/v1/rbac")
public class RbacController {

    private final RbacService rbacService;

    public RbacController(RbacService rbacService) {
        this.rbacService = rbacService;
    }

    @GetMapping("/roles")
    public Result<PageResult<SysRole>> pageRoles(PageQuery query) {
        return Result.ok(rbacService.pageRoles(query));
    }

    @GetMapping("/roles/all")
    public Result<List<SysRole>> listRoles() {
        return Result.ok(rbacService.listRoles());
    }

    @PostMapping("/roles")
    @RequiresPermission(code = BlogPermissions.ROLE_MANAGE)
    public Result<SysRole> createRole(@Valid @RequestBody RoleCreateRequest req) {
        return Result.ok(rbacService.createRole(req.roleCode(), req.roleName(), req.remark()));
    }

    @GetMapping("/permissions")
    public Result<PageResult<SysPermission>> pagePermissions(PageQuery query) {
        return Result.ok(rbacService.pagePermissions(query));
    }

    @GetMapping("/permissions/all")
    public Result<List<SysPermission>> listPermissions() {
        return Result.ok(rbacService.listPermissions());
    }

    @PostMapping("/user-roles")
    @RequiresPermission(code = BlogPermissions.USER_ROLE_MANAGE)
    public Result<Void> grantRole(@Valid @RequestBody UserRoleRequest req) {
        rbacService.grantRoleToUser(req.userId(), req.roleId());
        return Result.ok();
    }

    @DeleteMapping("/user-roles")
    @RequiresPermission(code = BlogPermissions.USER_ROLE_MANAGE)
    public Result<Void> revokeRole(@RequestParam("userId") Long userId, @RequestParam("roleId") Long roleId) {
        rbacService.revokeRoleFromUser(userId, roleId);
        return Result.ok();
    }

    @PostMapping("/role-permissions")
    @RequiresPermission(code = BlogPermissions.ROLE_MANAGE)
    public Result<Void> bindPermission(@Valid @RequestBody RolePermissionRequest req) {
        rbacService.bindPermissionToRole(req.roleId(), req.permissionId());
        return Result.ok();
    }

    @DeleteMapping("/role-permissions")
    @RequiresPermission(code = BlogPermissions.ROLE_MANAGE)
    public Result<Void> unbindPermission(
            @RequestParam("roleId") Long roleId, @RequestParam("permissionId") Long permissionId) {
        rbacService.unbindPermissionFromRole(roleId, permissionId);
        return Result.ok();
    }
}
