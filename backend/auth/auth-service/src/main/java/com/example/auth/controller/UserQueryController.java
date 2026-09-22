package com.example.auth.controller;

import com.example.auth.api.AuthPaths;
import com.example.auth.api.UserApis;
import com.example.auth.common.Result;
import com.example.auth.service.UserQueryService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 用户只读：选人 / 批量补洞。不含凭证。 */
@RestController
@RequestMapping(AuthPaths.USERS)
public class UserQueryController {

    private final UserQueryService userQueryService;

    public UserQueryController(UserQueryService userQueryService) {
        this.userQueryService = userQueryService;
    }

    @GetMapping
    public Result<UserApis.UserPageResult> search(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "current", defaultValue = "1") long current,
            @RequestParam(value = "size", defaultValue = "10") long size) {
        return Result.ok(userQueryService.search(keyword, current, size));
    }

    @GetMapping("/batch")
    public Result<List<UserApis.UserBrief>> batch(@RequestParam("ids") List<Long> ids) {
        return Result.ok(userQueryService.batch(ids));
    }
}
