package com.example.demoadmin.rbac;

import com.example.common.jwt.JwtVerifier;
import com.example.common.web.UserHeaders;
import java.util.Map;

/**
 * 投影同步：登录 upsert / 赋权快照 / batch 补洞。
 * 授权不依赖投影 status。
 */
public class UserRefSync {

    public record UserRef(long userId, String username, String realName, int status, long deptId, String deptName) {
    }

    public interface UserRefStore {
        void upsert(UserRef ref);
    }

    public interface AuthUserApi {
        UserRef batchOne(long userId);

        Map<Long, UserRef> batch(java.util.List<Long> ids);
    }

    private final UserRefStore store;
    private final AuthUserApi api;

    public UserRefSync(UserRefStore store, AuthUserApi api) {
        this.store = store;
        this.api = api;
    }

    /** 登录/进入系统时 upsert。 */
    public void onLogin(JwtVerifier verifier, String bearer) {
        var claims = verifier.parse(bearer.substring(7));
        long userId = Long.parseLong(verifier.uid(claims));
        UserRef ref = api.batchOne(userId);
        if (ref != null) {
            try {
                store.upsert(ref);
            } catch (Exception ignored) {
                // 投影失败不影响登录/授权
            }
        }
    }

    /** 成员列表补洞。 */
    public void fillMissing(java.util.List<Long> userIds) {
        try {
            Map<Long, UserRef> map = api.batch(userIds);
            map.values().forEach(store::upsert);
        } catch (Exception ignored) {
            // 补洞失败不阻断
        }
    }

    public String userIdHeaderName() {
        return UserHeaders.USER_ID;
    }
}
