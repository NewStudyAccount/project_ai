package com.qjj.auth.service.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Set;

public record AuthPrincipal(Long userId, String username, Set<String> permissions) implements UserDetails {

    public AuthPrincipal(Long userId, String username) {
        this(userId, username, Set.of());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return permissions.stream().map(SimpleGrantedAuthority::new).toList();
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        // SAS principalName / JWT sub 必须是用户中心 user.id 字符串，不能是登录名
        return String.valueOf(userId);
    }

    /** 登录名（展示用 preferred_username） */
    public String displayName() {
        return username;
    }

    public String getName() {
        return String.valueOf(userId);
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
