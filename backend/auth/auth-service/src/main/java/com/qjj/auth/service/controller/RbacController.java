package com.qjj.auth.service.controller;

import com.qjj.auth.common.result.Result;
import com.qjj.auth.framework.idempotent.Idempotent;
import com.qjj.auth.framework.idempotent.RateLimit;
import com.qjj.auth.service.dto.IdsRequest;
import com.qjj.auth.service.dto.MenuRequest;
import com.qjj.auth.service.dto.RoleRequest;
import com.qjj.auth.service.service.RbacService;
import com.qjj.auth.service.vo.MenuVO;
import com.qjj.auth.service.vo.RoleVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RbacController {
    private final RbacService rbacService;

    @GetMapping("/menus")
    @PreAuthorize("hasAuthority('auth:menu:list')")
    public Result<List<MenuVO>> menus() {
        return Result.ok(rbacService.menus());
    }

    @PostMapping("/menus")
    @PreAuthorize("hasAuthority('auth:menu:create')")
    @Idempotent(keyPrefix = "auth-menu-create")
    @RateLimit(keyPrefix = "auth-menu-create", permits = 30, windowSeconds = 60)
    public Result<MenuVO> createMenu(@Valid @RequestBody MenuRequest request) {
        return Result.ok(rbacService.createMenu(request));
    }

    @PutMapping("/menus/{id}")
    @PreAuthorize("hasAuthority('auth:menu:update')")
    @Idempotent(keyPrefix = "auth-menu-update")
    public Result<MenuVO> updateMenu(@PathVariable String id, @Valid @RequestBody MenuRequest request) {
        return Result.ok(rbacService.updateMenu(id, request));
    }

    @DeleteMapping("/menus/{id}")
    @PreAuthorize("hasAuthority('auth:menu:delete')")
    @Idempotent(keyPrefix = "auth-menu-delete")
    public Result<Void> deleteMenu(@PathVariable String id) {
        rbacService.deleteMenu(id);
        return Result.ok();
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('auth:role:list')")
    public Result<List<RoleVO>> roles() {
        return Result.ok(rbacService.roles());
    }

    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('auth:role:create')")
    @Idempotent(keyPrefix = "auth-role-create")
    @RateLimit(keyPrefix = "auth-role-create", permits = 30, windowSeconds = 60)
    public Result<RoleVO> createRole(@Valid @RequestBody RoleRequest request) {
        return Result.ok(rbacService.createRole(request));
    }

    @PutMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('auth:role:update')")
    @Idempotent(keyPrefix = "auth-role-update")
    public Result<RoleVO> updateRole(@PathVariable String id, @Valid @RequestBody RoleRequest request) {
        return Result.ok(rbacService.updateRole(id, request));
    }

    @DeleteMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('auth:role:delete')")
    @Idempotent(keyPrefix = "auth-role-delete")
    public Result<Void> deleteRole(@PathVariable String id) {
        rbacService.deleteRole(id);
        return Result.ok();
    }

    @PutMapping("/roles/{id}/menus")
    @PreAuthorize("hasAuthority('auth:role:assign')")
    @Idempotent(keyPrefix = "auth-role-menu")
    public Result<Void> assignMenus(@PathVariable String id, @RequestBody IdsRequest request) {
        rbacService.assignRoleMenus(id, request);
        return Result.ok();
    }

    @PostMapping("/users/{id}/roles")
    @PreAuthorize("hasAuthority('auth:role:assign')")
    @Idempotent(keyPrefix = "auth-user-role")
    public Result<Void> assignRoles(@PathVariable String id, @RequestBody IdsRequest request) {
        rbacService.assignUserRoles(id, request);
        return Result.ok();
    }

    @GetMapping("/me/menus")
    public Result<List<MenuVO>> meMenus() {
        return Result.ok(rbacService.myMenus());
    }

    @GetMapping("/me/permissions")
    public Result<Set<String>> mePermissions() {
        return Result.ok(rbacService.myPermissions());
    }
}
