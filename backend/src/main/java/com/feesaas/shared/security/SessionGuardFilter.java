package com.feesaas.shared.security;

import com.feesaas.shared.error.ErrorCode;
import com.feesaas.shared.error.ProblemResponses;
import com.feesaas.shared.tenancy.TenantClaims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Access JWTs carry {@code ver}. Password change / logout-all bumps users.token_version;
 * this filter rejects stale access tokens even if the signature is still valid.
 * Tenant suspension is enforced here so a still-valid JWT cannot keep using the API.
 */
@Component
public class SessionGuardFilter extends OncePerRequestFilter {

    private final JdbcClient jdbc;
    private final ProblemResponses problems;

    public SessionGuardFilter(JdbcClient jdbc, ProblemResponses problems) {
        this.jdbc = jdbc;
        this.problems = problems;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwt) || !auth.isAuthenticated()) {
            chain.doFilter(request, response);
            return;
        }
        UUID userId;
        try {
            userId = UUID.fromString(jwt.getToken().getSubject());
        } catch (RuntimeException e) {
            problems.write(response, ErrorCode.UNAUTHENTICATED, "Invalid credentials.");
            return;
        }
        Number verClaim = jwt.getToken().getClaim(TenantClaims.TOKEN_VERSION);
        int presentedVersion = verClaim == null ? -1 : verClaim.intValue();

        var row = jdbc.sql("select token_version, user_status, tenant_status from auth_session_state(:id)")
                .param("id", userId)
                .query((rs, i) -> new SessionRow(
                        rs.getInt("token_version"),
                        rs.getString("user_status"),
                        rs.getString("tenant_status")))
                .optional();
        if (row.isEmpty()) {
            problems.write(response, ErrorCode.UNAUTHENTICATED, "Invalid credentials.");
            return;
        }
        SessionRow session = row.get();
        if (session.tokenVersion != presentedVersion || !"ACTIVE".equals(session.userStatus)) {
            problems.write(response, ErrorCode.UNAUTHENTICATED, "Invalid credentials.");
            return;
        }
        String ctx = jwt.getToken().getClaimAsString(TenantClaims.CTX);
        boolean businessCtx = ctx == null || ctx.isBlank() || "BUSINESS".equals(ctx);
        if (businessCtx && ("SUSPENDED".equals(session.tenantStatus) || "CANCELLED".equals(session.tenantStatus))) {
            String impersonator = jwt.getToken().getClaimAsString(TenantClaims.IMPERSONATOR);
            if (impersonator == null || impersonator.isBlank()) {
                problems.write(response, ErrorCode.TENANT_SUSPENDED, "This business account is suspended.");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private record SessionRow(int tokenVersion, String userStatus, String tenantStatus) {}
}
