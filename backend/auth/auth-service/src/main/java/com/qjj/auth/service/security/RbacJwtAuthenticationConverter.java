package com.qjj.auth.service.security;

import com.qjj.auth.service.service.RbacService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.Set;

/**
 * JWT 验签后按 sub（user.id）从本库 RBAC 装载 authorities。
 * Claims 最小集不含权限码（设计约束），@PreAuthorize 依赖此处装配，而非 token 内嵌权限。
 */
public class RbacJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private final RbacService rbacService;

    public RbacJwtAuthenticationConverter(RbacService rbacService) {
        this.rbacService = rbacService;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String sub = jwt.getSubject();
        Set<String> permissions = Set.of();
        if (sub != null && !sub.isBlank()) {
            try {
                permissions = rbacService.permissionsForUser(Long.valueOf(sub.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        List<GrantedAuthority> authorities = permissions.stream()
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
        return new JwtAuthenticationToken(jwt, authorities, sub);
    }
}
