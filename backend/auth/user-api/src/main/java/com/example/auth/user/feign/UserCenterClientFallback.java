package com.example.auth.user.feign;

import com.example.auth.common.ErrorCode;
import com.example.auth.common.Result;
import com.example.auth.user.dto.UserProfileDto;
import com.example.auth.user.dto.UserSummaryDto;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 用户中心降级：不可用时返回系统错误码，由登录侧映射 503 语义。
 */
@Component
public class UserCenterClientFallback implements FallbackFactory<UserCenterClient> {

    @Override
    public UserCenterClient create(Throwable cause) {
        return new UserCenterClient() {
            @Override
            public Result<UserSummaryDto> getByUsername(String username) {
                return Result.fail(ErrorCode.USER_CENTER_UNAVAILABLE);
            }

            @Override
            public Result<UserSummaryDto> getById(Long id) {
                return Result.fail(ErrorCode.USER_CENTER_UNAVAILABLE);
            }

            @Override
            public Result<UserProfileDto> getProfile(Long id) {
                return Result.fail(ErrorCode.USER_CENTER_UNAVAILABLE);
            }
        };
    }
}
