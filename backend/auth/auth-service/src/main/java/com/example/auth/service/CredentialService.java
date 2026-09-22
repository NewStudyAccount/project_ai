package com.example.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.auth.common.BizException;
import com.example.auth.config.AuthProperties;
import com.example.auth.entity.SysLoginLog;
import com.example.auth.entity.SysUser;
import com.example.auth.mapper.SysLoginLogMapper;
import com.example.auth.mapper.SysUserMapper;
import java.time.LocalDateTime;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 凭证校验、失败限流、登录日志。 */
@Service
public class CredentialService {

    private static final String FAIL_KEY = "auth:fail:login:";

    private final SysUserMapper userMapper;
    private final SysLoginLogMapper loginLogMapper;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redis;
    private final AuthProperties properties;
    private final IdService idService;

    public CredentialService(
            SysUserMapper userMapper,
            SysLoginLogMapper loginLogMapper,
            PasswordEncoder passwordEncoder,
            StringRedisTemplate redis,
            AuthProperties properties,
            IdService idService) {
        this.userMapper = userMapper;
        this.loginLogMapper = loginLogMapper;
        this.passwordEncoder = passwordEncoder;
        this.redis = redis;
        this.properties = properties;
        this.idService = idService;
    }

    @Transactional
    public SysUser authenticate(String username, String rawPassword, String ip, String ua) {
        String failKey = FAIL_KEY + username;
        String fails = redis.opsForValue().get(failKey);
        if (fails != null && Integer.parseInt(fails) >= properties.getLoginMaxFail()) {
            throw BizException.locked();
        }

        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username));
        if (user == null || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            long count = redis.opsForValue().increment(failKey) == null
                    ? 1L : Long.parseLong(redis.opsForValue().get(failKey));
            redis.expire(failKey, java.time.Duration.ofSeconds(properties.getLoginLockSeconds()));
            writeLog(null, username, ip, ua, 0, "用户名或密码错误");
            throw BizException.auth("用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            writeLog(user.getId(), username, ip, ua, 0, "账号停用");
            throw BizException.disabled();
        }

        redis.delete(failKey);
        user.setLastLoginTime(LocalDateTime.now());
        user.setLastLoginIp(ip == null ? "" : ip);
        userMapper.updateById(user);
        writeLog(user.getId(), username, ip, ua, 1, "");
        return user;
    }

    private void writeLog(Long userId, String username, String ip, String ua, int status, String msg) {
        SysLoginLog log = new SysLoginLog();
        log.setId(idService.nextId("login_log"));
        log.setUserId(userId == null ? 0L : userId);
        log.setUsername(username == null ? "" : username);
        log.setLoginIp(ip == null ? "" : ip);
        log.setUserAgent(ua == null ? "" : ua);
        log.setStatus(status);
        log.setMessage(msg);
        log.setLoginTime(LocalDateTime.now());
        loginLogMapper.insert(log);
    }
}
