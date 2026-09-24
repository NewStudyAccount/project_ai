package com.qjj.user.api.feign;

import com.qjj.user.api.dto.UserBasicVO;
import com.qjj.user.api.dto.UserProfileVO;
import com.qjj.user.api.dto.UsernameStatusVO;
import com.qjj.user.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 用户中心对内查询契约。只允许服务间调用，网关与 Nginx 不对外路由 /internal。
 */
@FeignClient(name = "user-service", url = "${user.feign.user-center.base-url}", path = "/internal/users", fallback = UserQueryClientFallback.class)
public interface UserQueryClient {

    @GetMapping("/{id}")
    Result<UserBasicVO> getById(@PathVariable("id") String id);

    @GetMapping("/by-username/{username}")
    Result<UsernameStatusVO> getByUsername(@PathVariable("username") String username);

    @GetMapping("/{id}/profile")
    Result<UserProfileVO> getProfile(@PathVariable("id") String id);
}
