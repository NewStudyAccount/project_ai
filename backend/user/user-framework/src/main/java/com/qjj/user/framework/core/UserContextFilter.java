package com.qjj.user.framework.core;

import com.qjj.user.common.constants.CommonConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 解析网关注入的 X-User-* 进入 {@link UserContext}；请求结束清理。
 * 服务内以网关注入（覆盖写入）的值为准，客户端自带同名头会被网关覆盖。
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 50)
public class UserContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String userId = request.getHeader(CommonConstants.HEADER_USER_ID);
            String userName = request.getHeader(CommonConstants.HEADER_USER_NAME);
            if (userId != null && !userId.isBlank()) {
                UserContext ctx = new UserContext();
                try {
                    ctx.setUserId(Long.valueOf(userId.trim()));
                } catch (NumberFormatException ignored) {
                    // 非法 X-User-Id 不进入上下文（网关注入值异常时按未登录处理，由鉴权层拦截）
                }
                ctx.setUserName(userName);
                UserContext.set(ctx);
            }
            filterChain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }
}
