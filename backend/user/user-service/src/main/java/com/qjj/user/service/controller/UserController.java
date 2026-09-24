package com.qjj.user.service.controller;

import com.qjj.user.common.result.PageResult;
import com.qjj.user.common.result.Result;
import com.qjj.user.framework.idempotent.Idempotent;
import com.qjj.user.framework.idempotent.RateLimit;
import com.qjj.user.service.dto.CreateUserRequest;
import com.qjj.user.service.dto.RoleAssignRequest;
import com.qjj.user.service.dto.UpdateStatusRequest;
import com.qjj.user.service.dto.UpdateUserRequest;
import com.qjj.user.service.dto.UserPageQuery;
import com.qjj.user.service.service.RbacService;
import com.qjj.user.service.service.UserService;
import com.qjj.user.service.vo.UserVO;
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

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    private final RbacService rbacService;

    @GetMapping
    @RateLimit(keyPrefix = "user-list", dimension = RateLimit.Dimension.USER, permits = 30)
    public Result<PageResult<UserVO>> page(@Valid UserPageQuery query) {
        return Result.ok(userService.page(query));
    }

    @GetMapping("/{id}")
    public Result<UserVO> get(@PathVariable String id) {
        return Result.ok(userService.get(id));
    }

    @PostMapping
    @Idempotent(keyPrefix = "user-create")
    public Result<UserVO> create(@Valid @RequestBody CreateUserRequest request) {
        return Result.ok(userService.create(request));
    }

    @PutMapping("/{id}")
    @Idempotent(keyPrefix = "user-update")
    public Result<UserVO> update(@PathVariable String id, @Valid @RequestBody UpdateUserRequest request) {
        return Result.ok(userService.update(id, request));
    }

    @PostMapping("/{id}/status")
    @Idempotent(keyPrefix = "user-status")
    public Result<UserVO> updateStatus(@PathVariable String id, @Valid @RequestBody UpdateStatusRequest request) {
        return Result.ok(userService.updateStatus(id, request));
    }

    @PostMapping("/{id}/roles")
    @Idempotent(keyPrefix = "user-role-assign")
    public Result<Void> assignRoles(@PathVariable String id, @Valid @RequestBody RoleAssignRequest request) {
        rbacService.assignUserRoles(id, request);
        return Result.ok();
    }

    @DeleteMapping("/{id}/roles")
    @Idempotent(keyPrefix = "user-role-remove")
    public Result<Void> removeRoles(@PathVariable String id, @Valid @RequestBody RoleAssignRequest request) {
        rbacService.removeUserRoles(id, request);
        return Result.ok();
    }
}
