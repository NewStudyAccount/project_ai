package com.qjj.user.api.feign;

import com.qjj.user.api.dto.UserBasicVO;
import com.qjj.user.api.dto.UserProfileVO;
import com.qjj.user.api.dto.UsernameStatusVO;
import com.qjj.user.common.enums.UserErrorCodeEnum;
import com.qjj.user.common.result.Result;
import org.springframework.stereotype.Component;

/**
 * 用户查询 Fallback。必须存在且不吞异常，调用方按统一错误码分流。
 */
@Component
public class UserQueryClientFallback implements UserQueryClient {

    @Override
    public Result<UserBasicVO> getById(String id) {
        return Result.fail(UserErrorCodeEnum.USER_NOT_FOUND);
    }

    @Override
    public Result<UsernameStatusVO> getByUsername(String username) {
        return Result.fail(UserErrorCodeEnum.USER_NOT_FOUND);
    }

    @Override
    public Result<UserProfileVO> getProfile(String id) {
        return Result.fail(UserErrorCodeEnum.USER_NOT_FOUND);
    }
}
