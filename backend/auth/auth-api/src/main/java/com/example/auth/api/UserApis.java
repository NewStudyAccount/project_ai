package com.example.auth.api;

import java.util.List;

/**
 * 用户只读契约（无凭证字段）。
 */
public final class UserApis {

    private UserApis() {
    }

    public record UserBrief(
            Long id,
            String username,
            String realName,
            Integer status,
            Long deptId,
            String deptName
    ) {
    }

    public record UserPageResult(
            List<UserBrief> records,
            long total,
            long size,
            long current
    ) {
    }

    public record BatchUserRequest(
            List<Long> ids
    ) {
    }
}
