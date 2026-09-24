package com.qjj.user.service.controller;

import com.qjj.user.common.result.Result;
import com.qjj.user.service.service.RbacService;
import com.qjj.user.service.vo.MenuVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class MeController {

    private final RbacService rbacService;

    @GetMapping("/menus")
    public Result<List<MenuVO>> menus() {
        return Result.ok(rbacService.myMenus());
    }

    @GetMapping("/permissions")
    public Result<Set<String>> permissions() {
        return Result.ok(rbacService.myPermissions());
    }
}
