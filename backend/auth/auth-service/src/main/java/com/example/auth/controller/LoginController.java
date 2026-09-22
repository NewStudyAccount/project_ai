package com.example.auth.controller;

import com.example.auth.common.Result;
import com.example.auth.dto.LoginRequest;
import com.example.auth.dto.SessionLoginResponse;
import com.example.auth.service.LoginService;
import com.example.auth.service.SessionService;
import com.example.auth.user.dto.UserSummaryDto;
import com.example.auth.service.UserResolveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录页 API；不提供注册/找回。
 */
@Tag(name = "登录")
@RestController
@RequestMapping("/api/v1/auth")
public class LoginController {

    public static final String SESSION_COOKIE = "AUTH_SESSION";

    private final LoginService loginService;
    private final SessionService sessionService;
    private final UserResolveService userResolveService;

    public LoginController(LoginService loginService, SessionService sessionService,
                           UserResolveService userResolveService) {
        this.loginService = loginService;
        this.sessionService = sessionService;
        this.userResolveService = userResolveService;
    }

    @PostMapping("/login")
    @Operation(summary = "账密登录并建立 SSO 会话")
    public ResponseEntity<Result<SessionLoginResponse>> login(@Valid @RequestBody LoginRequest request,
                                                              HttpServletRequest http,
                                                              HttpServletResponse response) {
        String raw = loginService.login(request, http);
        UserSummaryDto user = userResolveService.requireByUsername(request.username().trim());
        ResponseCookie cookie = ResponseCookie.from(SESSION_COOKIE, raw)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofHours(12))
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(Result.ok(new SessionLoginResponse(raw, user.id(), user.username())));
    }

    @PostMapping("/logout")
    @Operation(summary = "退出并吊销会话")
    public Result<Void> logout(@org.springframework.web.bind.annotation.CookieValue(name = SESSION_COOKIE, required = false) String sessionToken) {
        if (sessionToken != null) {
            sessionService.revokeByToken(sessionToken);
        }
        return Result.ok();
    }
}
