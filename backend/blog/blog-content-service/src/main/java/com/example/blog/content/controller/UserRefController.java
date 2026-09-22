package com.example.blog.content.controller;

import com.example.blog.common.audit.CurrentUser;
import com.example.blog.common.result.Result;
import com.example.blog.content.entity.SysUserRef;
import com.example.blog.content.service.UserRefService;
import com.example.blog.content.vo.UserRefVo;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 用户投影：登录 upsert、展示 batch 补洞。 */
@RestController
@RequestMapping("/api/v1/user-refs")
public class UserRefController {

    private final UserRefService userRefService;

    public UserRefController(UserRefService userRefService) {
        this.userRefService = userRefService;
    }

    /** 进入 blog 时触发投影 upsert（幂等）。 */
    @PostMapping("/sync")
    public Result<Void> syncMe(@RequestParam(value = "username", required = false) String username) {
        Long uid = CurrentUser.idOrNull();
        if (uid == null) {
            return Result.ok();
        }
        try {
            userRefService.upsertFromLogin(uid, username == null ? CurrentUser.username() : username, "");
        } catch (Exception ignored) {
            // 投影失败不影响授权与主流程
        }
        return Result.ok();
    }

    @GetMapping("/batch")
    public Result<List<UserRefVo>> batch(@RequestParam("userIds") List<Long> userIds) {
        Map<Long, SysUserRef> map = userRefService.batchForDisplay(userIds);
        List<UserRefVo> list = map.values().stream()
                .map(r -> new UserRefVo(r.getUserId(), r.getUsername(), r.getRealName(), r.getStatus(), r.getSyncTime()))
                .collect(Collectors.toList());
        return Result.ok(list);
    }
}
