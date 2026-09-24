package com.qjj.user.service.controller;

import com.qjj.user.common.result.Result;
import com.qjj.user.framework.idempotent.Idempotent;
import com.qjj.user.service.dto.MenuRequest;
import com.qjj.user.service.service.RbacService;
import com.qjj.user.service.vo.MenuVO;
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
@RequestMapping("/api/v1/menus")
@RequiredArgsConstructor
public class MenuController {

    private final RbacService rbacService;

    @GetMapping
    public Result<List<MenuVO>> tree() {
        return Result.ok(rbacService.listMenuTree());
    }

    @PostMapping
    @Idempotent(keyPrefix = "menu-create")
    public Result<MenuVO> create(@Valid @RequestBody MenuRequest request) {
        return Result.ok(rbacService.createMenu(request));
    }

    @PutMapping("/{id}")
    @Idempotent(keyPrefix = "menu-update")
    public Result<MenuVO> update(@PathVariable String id, @Valid @RequestBody MenuRequest request) {
        return Result.ok(rbacService.updateMenu(id, request));
    }

    @DeleteMapping("/{id}")
    @Idempotent(keyPrefix = "menu-delete")
    public Result<Void> delete(@PathVariable String id) {
        rbacService.deleteMenu(id);
        return Result.ok();
    }
}
