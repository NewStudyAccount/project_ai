package com.qjj.user.service.controller;

import com.qjj.user.api.dto.UserBasicVO;
import com.qjj.user.api.dto.UserProfileVO;
import com.qjj.user.api.dto.UsernameStatusVO;
import com.qjj.user.common.result.Result;
import com.qjj.user.service.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 对内查询接口。只经 Nacos 服务间调用，网关与 Nginx 不路由 /internal。
 */
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserService userService;

    @GetMapping("/{id}")
    public Result<UserBasicVO> getById(@PathVariable String id) {
        return Result.ok(userService.getBasic(id));
    }

    @GetMapping("/by-username/{username}")
    public Result<UsernameStatusVO> getByUsername(@PathVariable String username) {
        return Result.ok(userService.getByUsername(username));
    }

    @GetMapping("/{id}/profile")
    public Result<UserProfileVO> getProfile(@PathVariable String id) {
        return Result.ok(userService.getProfile(id));
    }
}
