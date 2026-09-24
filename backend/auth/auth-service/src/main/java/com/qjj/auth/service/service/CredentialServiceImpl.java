package com.qjj.auth.service.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qjj.auth.common.enums.AuthErrorCodeEnum;
import com.qjj.auth.common.exception.BizException;
import com.qjj.auth.service.entity.SysCredential;
import com.qjj.auth.service.mapper.SysCredentialMapper;
import com.qjj.auth.service.security.AuthPrincipal;
import com.qjj.user.api.dto.UsernameStatusVO;
import com.qjj.user.api.feign.UserQueryClient;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CredentialServiceImpl implements CredentialService {
    private final SysCredentialMapper credentialMapper;
    private final UserQueryClient userQueryClient;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;
    private final RbacService rbacService;

    @Override
    public AuthPrincipal authenticate(String username, String rawPassword) {
        var userResult = userQueryClient.getByUsername(username);
        if (userResult.getCode() != 0 || userResult.getData() == null) {
            throw new BizException(AuthErrorCodeEnum.LOGIN_FAILED);
        }
        UsernameStatusVO user = userResult.getData();
        if (user.getStatus() == null || user.getStatus() != 1) {
            auditService.record("LOGIN_FAILED", "user", user.getId(), "账号不可用");
            throw new BizException(AuthErrorCodeEnum.LOGIN_FAILED);
        }
        SysCredential credential = credentialMapper.selectOne(new LambdaQueryWrapper<SysCredential>()
                .eq(SysCredential::getUserId, Long.parseLong(user.getId()))
                .eq(SysCredential::getCredentialType, "PASSWORD"));
        String secretRef = credential == null ? null : credential.getSecretRef();
        if (secretRef != null && !secretRef.startsWith("{")) {
            secretRef = "{bcrypt}" + secretRef;
        }
        if (credential == null || !passwordEncoder.matches(rawPassword, secretRef)) {
            auditService.record("LOGIN_FAILED", "user", user.getId(), "密码校验失败");
            throw new BizException(AuthErrorCodeEnum.LOGIN_FAILED);
        }
        return new AuthPrincipal(Long.valueOf(user.getId()), user.getUsername(),
                rbacService.permissionsForUser(Long.valueOf(user.getId())));
    }
}
