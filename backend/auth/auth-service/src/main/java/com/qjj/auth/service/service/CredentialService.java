package com.qjj.auth.service.service;

import com.qjj.auth.service.security.AuthPrincipal;

public interface CredentialService {
    AuthPrincipal authenticate(String username, String rawPassword);
}
