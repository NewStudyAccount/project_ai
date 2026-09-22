package com.example.blog.content.service;

import com.example.blog.common.auth.PermissionChecker;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 权限码鉴权：Redis 缓存优先，未命中回源 DB。
 */
@Service
public class BlogPermissionChecker implements PermissionChecker {

    private static final String PERM_KEY = "blog:perm:";
    private static final long TTL_SECONDS = 300L;

    private final RbacService rbacService;
    private final StringRedisTemplate redis;

    public BlogPermissionChecker(RbacService rbacService, StringRedisTemplate redis) {
        this.rbacService = rbacService;
        this.redis = redis;
    }

    @Override
    public Set<String> permissionCodes(long userId) {
        String key = PERM_KEY + userId;
        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null) {
                return Set.of(cached.split(","));
            }
        } catch (Exception ignored) {
            // Redis 不可用则回源，不阻断鉴权
        }
        Set<String> codes = rbacService.permissionCodesOfUser(userId);
        try {
            if (codes.isEmpty()) {
                redis.opsForValue().set(key, "", TTL_SECONDS, TimeUnit.SECONDS);
            } else {
                redis.opsForValue().set(key, String.join(",", codes), TTL_SECONDS, TimeUnit.SECONDS);
            }
        } catch (Exception ignored) {
            // 缓存写失败不影响鉴权
        }
        return codes;
    }

    /** 赋权/角色权限变更后失效。 */
    public void evict(long userId) {
        try {
            redis.delete(PERM_KEY + userId);
        } catch (Exception ignored) {
            // 尽力失效
        }
    }

    /** 角色绑定变更：简单起见可对相关用户逐个失效；本期提供按用户失效即可。 */
    public void evictByUserId(Long userId) {
        if (userId != null) {
            evict(userId);
        }
    }
}
