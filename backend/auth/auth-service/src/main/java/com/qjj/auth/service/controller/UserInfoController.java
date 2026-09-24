package com.qjj.auth.service.controller;

import com.qjj.auth.common.result.Result;
import com.qjj.user.api.dto.UserProfileVO;
import com.qjj.user.api.feign.UserQueryClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class UserInfoController {
    private final UserQueryClient userQueryClient;

    @GetMapping("/oauth2/userinfo")
    public ResponseEntity<Result<Map<String, Object>>> userinfo(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Result.fail(com.qjj.auth.common.enums.SysErrorCodeEnum.UNAUTHENTICATED));
        }
        String userId = authentication.getName();
        UserProfileVO profile = userQueryClient.getProfile(userId).getData();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", userId);
        claims.put("preferred_username", profile == null ? "" : profile.getUsername());
        claims.put("name", profile == null ? "" : profile.getRealName());
        claims.put("email", profile == null ? "" : profile.getEmail());
        claims.put("phone", profile == null ? "" : profile.getPhone());
        return ResponseEntity.ok(Result.ok(claims));
    }
}
