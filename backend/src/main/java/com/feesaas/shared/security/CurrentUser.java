package com.feesaas.shared.security;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import com.feesaas.shared.error.ApiException;
import com.feesaas.shared.error.ErrorCode;
import com.feesaas.shared.tenancy.TenantClaims;

public final class CurrentUser {
    private CurrentUser() {}

    public static UUID id() {
        return UUID.fromString(jwt().getToken().getSubject());
    }

    public static String role() {
        String role = jwt().getToken().getClaimAsString(TenantClaims.ROLE);
        if (role == null || role.isBlank()) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "Invalid credentials.");
        }
        return role;
    }

    private static JwtAuthenticationToken jwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken token) || !auth.isAuthenticated()) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "Invalid credentials.");
        }
        return token;
    }
}
