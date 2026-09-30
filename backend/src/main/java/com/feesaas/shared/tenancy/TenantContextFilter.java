package com.feesaas.shared.tenancy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Derives the TenantScope from the VERIFIED JWT and nothing else.
 */
@Component
public class TenantContextFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantContextFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        TenantContext.set(resolve());
        try {
            chain.doFilter(req, res);
        } finally {
            TenantContext.clear();
        }
    }

    private TenantScope resolve() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwt) || !auth.isAuthenticated()) {
            return TenantScope.NONE;
        }
        UUID userId;
        try {
            userId = UUID.fromString(jwt.getToken().getSubject());
        } catch (RuntimeException e) {
            return TenantScope.NONE;
        }
        boolean platform = TenantClaims.PLATFORM_ROLE.equals(jwt.getToken().getClaimAsString(TenantClaims.ROLE));
        String ctx = jwt.getToken().getClaimAsString(TenantClaims.CTX);
        if (ctx == null || ctx.isBlank()) {
            if (platform) {
                ctx = "PLATFORM";
            } else if (TenantClaims.INDIVIDUAL_ROLE.equals(jwt.getToken().getClaimAsString(TenantClaims.ROLE))) {
                ctx = "PERSONAL";
            } else {
                ctx = "BUSINESS";
            }
        }
        UUID workspaceId = parseUuid(jwt.getToken().getClaimAsString(TenantClaims.WORKSPACE_ID));
        UUID groupId = parseUuid(jwt.getToken().getClaimAsString(TenantClaims.GROUP_ID));
        if ("PERSONAL".equals(ctx)) {
            return TenantScope.personal(userId, workspaceId);
        }
        if ("GROUP".equals(ctx) && groupId != null) {
            return TenantScope.group(userId, groupId);
        }
        String rawTenant = jwt.getToken().getClaimAsString(TenantClaims.TENANT_ID);
        if (rawTenant == null || rawTenant.isBlank()) {
            return platform ? TenantScope.platformAdmin().withUser(userId) : TenantScope.NONE;
        }
        try {
            UUID tenantId = UUID.fromString(rawTenant);
            TenantScope scope = platform ? TenantScope.platformFor(tenantId) : TenantScope.tenant(tenantId);
            return scope.withUser(userId);
        } catch (IllegalArgumentException e) {
            log.warn("Rejected malformed tenant_id claim");
            return TenantScope.NONE;
        }
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
