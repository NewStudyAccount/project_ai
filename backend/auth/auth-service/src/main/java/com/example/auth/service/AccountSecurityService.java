package com.example.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.auth.common.BizException;
import com.example.auth.entity.SysUser;
import com.example.auth.mapper.SysUserMapper;
import java.time.LocalDateTime;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 账号安全：改密、启停。 */
@Service
public class AccountSecurityService {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public AccountSecurityService(SysUserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void changePassword(long userId, String oldPassword, String newPassword) {
        SysUser user = require(userId);
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw BizException.auth("原密码不正确");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPwdUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
    }

    @Transactional
    public void updateStatus(long userId, int status) {
        SysUser user = require(userId);
        user.setStatus(status);
        userMapper.updateById(user);
    }

    private SysUser require(long userId) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getId, userId));
        if (user == null) {
            throw BizException.notFound();
        }
        return user;
    }
}
