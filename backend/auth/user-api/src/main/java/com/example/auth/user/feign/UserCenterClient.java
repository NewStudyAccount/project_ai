package com.example.auth.user.feign;

import com.example.auth.common.Result;
import com.example.auth.user.dto.UserProfileDto;
import com.example.auth.user.dto.UserSummaryDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 用户中心契约客户端；禁止 RestTemplate；必须 Fallback。
 */
@FeignClient(name = "user-center", path = "/api/v1/users", fallbackFactory = UserCenterClientFallback.class)
public interface UserCenterClient {

    @GetMapping("/by-username/{username}")
    Result<UserSummaryDto> getByUsername(@PathVariable("username") String username);

    @GetMapping("/{id}")
    Result<UserSummaryDto> getById(@PathVariable("id") Long id);

    @GetMapping("/{id}/profile")
    Result<UserProfileDto> getProfile(@PathVariable("id") Long id);
}
