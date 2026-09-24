package com.qjj.user.service.service;

import com.qjj.user.api.dto.UserBasicVO;
import com.qjj.user.api.dto.UserProfileVO;
import com.qjj.user.api.dto.UsernameStatusVO;
import com.qjj.user.common.result.PageResult;
import com.qjj.user.service.dto.CreateUserRequest;
import com.qjj.user.service.dto.UpdateStatusRequest;
import com.qjj.user.service.dto.UpdateUserRequest;
import com.qjj.user.service.dto.UserPageQuery;
import com.qjj.user.service.vo.UserVO;

public interface UserService {

    UserVO create(CreateUserRequest request);

    UserVO update(String id, UpdateUserRequest request);

    UserVO updateStatus(String id, UpdateStatusRequest request);

    UserVO get(String id);

    PageResult<UserVO> page(UserPageQuery query);

    UserBasicVO getBasic(String id);

    UsernameStatusVO getByUsername(String username);

    UserProfileVO getProfile(String id);
}
