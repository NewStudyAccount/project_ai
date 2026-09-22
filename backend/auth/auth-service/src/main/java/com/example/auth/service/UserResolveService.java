package com.example.auth.service;

import com.example.auth.common.BizException;
import com.example.auth.common.ErrorCode;
import com.example.auth.common.Result;
import com.example.auth.user.dto.UserProfileDto;
import com.example.auth.user.dto.UserSummaryDto;
import com.example.auth.user.feign.UserCenterClient;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * 登录解析 username → user_id + status；短 TTL 缓存；Feign 必须在事务外。
 */
@Service
public class UserResolveService {

    private static final long CACHE_TTL_MS = 45_000L;

    private final UserCenterClient userCenterClient;
    private final Map<String, CachedUser> usernameCache = new ConcurrentHashMap<>();

    public UserResolveService(UserCenterClient userCenterClient) {
        this.userCenterClient = userCenterClient;
    }

    public UserSummaryDto requireByUsername(String username) {
        UserSummaryDto user = getByUsername(username);
        if (user == null) {
            throw new BizException(ErrorCode.CREDENTIAL_INVALID);
        }
        return user;
    }

    public UserSummaryDto getByUsername(String username) {
        CachedUser cached = usernameCache.get(username);
        if (cached != null && !cached.expired()) {
            return cached.user();
        }
        Result<UserSummaryDto> result;
        try {
            result = userCenterClient.getByUsername(username);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.USER_CENTER_UNAVAILABLE);
        }
        if (result == null) {
            throw new BizException(ErrorCode.USER_CENTER_UNAVAILABLE);
        }
        if (result.code() == ErrorCode.USER_CENTER_UNAVAILABLE.code()) {
            throw new BizException(ErrorCode.USER_CENTER_UNAVAILABLE);
        }
        if (result.data() == null) {
            return null;
        }
        usernameCache.put(username, new CachedUser(result.data(), System.currentTimeMillis() + CACHE_TTL_MS));
        return result.data();
    }

    public UserProfileDto requireProfile(Long userId) {
        Result<UserProfileDto> result;
        try {
            result = userCenterClient.getProfile(userId);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.USER_CENTER_UNAVAILABLE);
        }
        if (result == null || result.data() == null) {
            throw new BizException(ErrorCode.USER_CENTER_UNAVAILABLE);
        }
        return result.data();
    }

    private record CachedUser(UserSummaryDto user, long expireAt) {
        boolean expired() {
            return System.currentTimeMillis() > expireAt;
        }
    }
}
