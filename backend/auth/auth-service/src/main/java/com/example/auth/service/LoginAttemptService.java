package com.example.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.auth.entity.LoginAttempt;
import com.example.auth.framework.IdService;
import com.example.auth.mapper.LoginAttemptMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 登录尝试与失败限制；防撞库统一文案。
 */
@Service
public class LoginAttemptService {

    private static final int MAX_FAIL_PER_USER = 5;
    private static final int MAX_FAIL_PER_IP = 20;
    private static final int WINDOW_MINUTES = 15;
    public static final String GENERIC_FAIL = "用户名或密码错误";

    private final LoginAttemptMapper loginAttemptMapper;
    private final IdService idService;

    public LoginAttemptService(LoginAttemptMapper loginAttemptMapper, IdService idService) {
        this.loginAttemptMapper = loginAttemptMapper;
        this.idService = idService;
    }

    @Transactional
    public void record(String username, Long userId, boolean success, String failReason,
                       String ip, String userAgent, String clientId) {
        LoginAttempt row = new LoginAttempt();
        row.setId(idService.nextId("login_attempt"));
        row.setUsername(username == null ? "" : username);
        row.setUserId(userId);
        row.setSuccess(success ? 1 : 0);
        row.setFailReason(failReason == null ? "" : failReason);
        row.setIp(ip == null ? "" : ip);
        row.setUserAgent(userAgent == null ? "" : userAgent);
        row.setClientId(clientId == null ? "" : clientId);
        loginAttemptMapper.insert(row);
    }

    @Transactional(readOnly = true)
    public boolean isLocked(String username, String ip) {
        LocalDateTime since = LocalDateTime.now().minusMinutes(WINDOW_MINUTES);
        long byUser = loginAttemptMapper.selectCount(new LambdaQueryWrapper<LoginAttempt>()
                .eq(LoginAttempt::getUsername, username)
                .eq(LoginAttempt::getSuccess, 0)
                .ge(LoginAttempt::getCreateTime, since));
        if (byUser >= MAX_FAIL_PER_USER) {
            return true;
        }
        long byIp = loginAttemptMapper.selectCount(new LambdaQueryWrapper<LoginAttempt>()
                .eq(LoginAttempt::getIp, ip)
                .eq(LoginAttempt::getSuccess, 0)
                .ge(LoginAttempt::getCreateTime, since));
        return byIp >= MAX_FAIL_PER_IP;
    }
}
