package com.qjj.user.service.controller;

import com.qjj.user.common.result.Result;
import com.qjj.user.framework.idempotent.Idempotent;
import com.qjj.user.service.dto.RoleMenuRequest;
import com.qjj.user.service.dto.RoleRequest;
import com.qjj.user.service.service.RbacService;
import com.qjj.user.service.vo.RoleVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RbacService rbacService;

    @GetMapping
    public Result<List<RoleVO>> list() {
        return Result.ok(rbacService.listRoles());
    }

    @PostMapping
    @Idempotent(keyPrefix = "role-create")
    public Result<RoleVO> create(@Valid @RequestBody RoleRequest request) {
        return Result.ok(rbacService.createRole(request));
    }

    @PutMapping("/{id}")
    @Idempotent(keyPrefix = "role-update")
    public Result<RoleVO> update(@PathVariable String id, @Valid @RequestBody RoleRequest request) {
        return Result.ok(rbacService.updateRole(id, request));
    }

    @DeleteMapping("/{id}")
    @Idempotent(keyPrefix = "role-delete")
    public Result<Void> delete(@PathVariable String id) {
        rbacService.deleteRole(id);
        return Result.ok();
    }

    @PutMapping("/{id}/menus")
    @Idempotent(keyPrefix = "role-menu-assign")
    public Result<Void> assignMenus(@PathVariable String id, @RequestBody RoleMenuRequest request) {
        rbacService.assignRoleMenus(id, request);
        return Result.ok();
    }
}
