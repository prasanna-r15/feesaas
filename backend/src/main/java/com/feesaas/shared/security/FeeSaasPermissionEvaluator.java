package com.feesaas.shared.security;

import java.io.Serializable;
import java.util.UUID;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class FeeSaasPermissionEvaluator implements PermissionEvaluator {

    private final PermissionLookup permissions;

    public FeeSaasPermissionEvaluator(PermissionLookup permissions) {
        this.permissions = permissions;
    }

    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
        if (!(authentication instanceof JwtAuthenticationToken jwt) || permission == null) {
            return false;
        }
        try {
            UUID userId = UUID.fromString(jwt.getToken().getSubject());
            return permissions.has(userId, permission.toString());
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public boolean hasPermission(
            Authentication authentication, Serializable targetId, String targetType, Object permission) {
        return hasPermission(authentication, targetId, permission);
    }
}
