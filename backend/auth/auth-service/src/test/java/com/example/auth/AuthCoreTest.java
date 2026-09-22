package com.example.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.auth.common.BizException;
import com.example.auth.common.ErrorCode;
import com.example.auth.common.enums.GrantStatusEnum;
import com.example.auth.common.enums.RefreshTokenStatusEnum;
import com.example.auth.entity.AuthGrant;
import com.example.auth.entity.AuthRefreshToken;
import com.example.auth.framework.IdService;
import com.example.auth.mapper.AuthGrantMapper;
import com.example.auth.mapper.AuthRefreshTokenMapper;
import com.example.auth.service.AuditService;
import com.example.auth.service.CredentialService;
import com.example.auth.service.TokenLedgerService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 凭证、RT 轮转/重用吊销、redirect 白名单相关核心行为。
 */
@ExtendWith(MockitoExtension.class)
class AuthCoreTest {

    @Mock
    AuthGrantMapper grantMapper;
    @Mock
    AuthRefreshTokenMapper refreshTokenMapper;
    @Mock
    IdService idService;
    @Mock
    AuditService auditService;
    @Mock
    com.example.auth.mapper.SysCredentialMapper credentialMapper;

    TokenLedgerService tokenLedgerService;
    CredentialService credentialService;

    @BeforeEach
    void setUp() {
        tokenLedgerService = new TokenLedgerService(grantMapper, refreshTokenMapper, idService, auditService);
        credentialService = new CredentialService(credentialMapper, idService);
    }

    @Test
    void hashToken_isSha256Hex64() {
        String hash = TokenLedgerService.hashToken("raw-token");
        assertEquals(64, hash.length());
        assertEquals(hash, TokenLedgerService.hashToken("raw-token"));
    }

    @Test
    void initPassword_rejectsBlank() {
        BizException ex = assertThrows(BizException.class,
                () -> credentialService.initPasswordCredential(1L, " "));
        assertEquals(ErrorCode.VALIDATION_ERROR.code(), ex.getCode());
    }

    @Test
    void matchesPassword_falseWhenNoCredential() {
        org.mockito.Mockito.when(credentialMapper.selectOne(org.mockito.ArgumentMatchers.any()))
                .thenReturn(null);
        assertFalse(credentialService.matchesPassword(1L, "pwd"));
    }

    @Test
    void rotatedToken_reuse_revokesGrantChain() {
        AuthRefreshToken used = new AuthRefreshToken();
        used.setId(100L);
        used.setGrantId(200L);
        used.setUserId(1L);
        used.setClientId("spa");
        used.setStatus(RefreshTokenStatusEnum.ROTATED.getCode());
        org.mockito.Mockito.when(refreshTokenMapper.selectOne(org.mockito.ArgumentMatchers.any()))
                .thenReturn(used);

        BizException ex = assertThrows(BizException.class,
                () -> tokenLedgerService.rotateRefreshToken("hash", 60));
        assertEquals(ErrorCode.TOKEN_REVOKED.code(), ex.getCode());
    }

    @Test
    void grantStatusEnum_codes() {
        assertEquals(1, GrantStatusEnum.ACTIVE.getCode());
        assertEquals(0, GrantStatusEnum.REVOKED.getCode());
    }

    @Test
    void redirectUri_whitelistIsExactMatch() {
        String registered = "https://app.example.com/callback,https://app.example.com/cb2";
        assertTrue(java.util.Arrays.stream(registered.split(","))
                .map(String::trim)
                .anyMatch(u -> u.equals("https://app.example.com/callback")));
        assertFalse(java.util.Arrays.stream(registered.split(","))
                .map(String::trim)
                .anyMatch(u -> u.equals("https://app.example.com/callback/evil")));
        assertFalse(java.util.Arrays.stream(registered.split(","))
                .map(String::trim)
                .anyMatch(u -> u.equals("https://evil.example.com/callback")));
    }
}
