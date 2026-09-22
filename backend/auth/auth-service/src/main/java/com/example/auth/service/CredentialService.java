package com.example.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.auth.common.BizException;
import com.example.auth.common.ErrorCode;
import com.example.auth.common.enums.CredentialTypeEnum;
import com.example.auth.entity.SysCredential;
import com.example.auth.framework.IdService;
import com.example.auth.mapper.SysCredentialMapper;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 凭证读写；密码仅存 secret_ref 哈希。
 */
@Service
public class CredentialService {

    private final SysCredentialMapper credentialMapper;
    private final IdService idService;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public CredentialService(SysCredentialMapper credentialMapper, IdService idService) {
        this.credentialMapper = credentialMapper;
        this.idService = idService;
    }

    @Transactional
    public void initPasswordCredential(Long userId, String rawPassword) {
        if (userId == null || rawPassword == null || rawPassword.isBlank()) {
            throw new BizException(ErrorCode.VALIDATION_ERROR);
        }
        SysCredential existing = findByUserId(userId, CredentialTypeEnum.PASSWORD);
        String hash = passwordEncoder.encode(rawPassword);
        if (existing == null) {
            SysCredential row = new SysCredential();
            row.setId(idService.nextId("sys_credential"));
            row.setUserId(userId);
            row.setCredentialType(CredentialTypeEnum.PASSWORD.getCode());
            row.setSecretRef(hash);
            row.setVerified(1);
            row.setStatus(1);
            row.setPwdUpdateTime(LocalDateTime.now());
            credentialMapper.insert(row);
        } else {
            existing.setSecretRef(hash);
            existing.setPwdUpdateTime(LocalDateTime.now());
            existing.setVerified(1);
            existing.setStatus(1);
            credentialMapper.updateById(existing);
        }
    }

    @Transactional(readOnly = true)
    public boolean matchesPassword(Long userId, String rawPassword) {
        SysCredential row = findByUserId(userId, CredentialTypeEnum.PASSWORD);
        if (row == null || row.getStatus() == null || row.getStatus() != 1) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, row.getSecretRef());
    }

    @Transactional(readOnly = true)
    public Optional<SysCredential> findActive(Long userId, CredentialTypeEnum type) {
        return Optional.ofNullable(findByUserId(userId, type));
    }

    private SysCredential findByUserId(Long userId, CredentialTypeEnum type) {
        return credentialMapper.selectOne(new LambdaQueryWrapper<SysCredential>()
                .eq(SysCredential::getUserId, userId)
                .eq(SysCredential::getCredentialType, type.getCode())
                .last("LIMIT 1"));
    }
}
