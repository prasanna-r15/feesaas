package com.feesaas.auth.application;

import com.feesaas.auth.domain.AuthUser;
import com.feesaas.auth.infra.AuthUserRepository;
import com.feesaas.auth.infra.AuthUserRepository.SessionSnapshot;
import com.feesaas.auth.infra.IdentityRepository;
import com.feesaas.auth.infra.PasswordResetRepository;
import com.feesaas.auth.infra.RefreshTokenRepository;
import com.feesaas.auth.infra.RefreshTokenRepository.StoredRefreshToken;
import com.feesaas.shared.error.ApiException;
import com.feesaas.shared.error.ErrorCode;
import com.feesaas.shared.security.AuthProperties;
import com.feesaas.shared.security.JwtService;
import com.feesaas.shared.security.TokenHasher;
import com.feesaas.shared.tenancy.TenantClaims;
import com.feesaas.shared.tenancy.TenantExecutor;
import com.feesaas.shared.tenancy.TenantScope;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String GENERIC_AUTH = "Invalid credentials.";

    private final AuthUserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordResetRepository passwordResets;
    private final PasswordEncoder passwords;
    private final JwtService jwt;
    private final AuthProperties properties;
    private final TenantExecutor tenants;
    private final IdentityRepository identity;
    private final Clock clock;
    private final String dummyHash;

    public AuthService(
            AuthUserRepository users,
            RefreshTokenRepository refreshTokens,
            PasswordResetRepository passwordResets,
            PasswordEncoder passwords,
            JwtService jwt,
            AuthProperties properties,
            TenantExecutor tenants,
            IdentityRepository identity,
            Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordResets = passwordResets;
        this.passwords = passwords;
        this.jwt = jwt;
        this.properties = properties;
        this.tenants = tenants;
        this.identity = identity;
        this.clock = clock;
        this.dummyHash = passwords.encode("feesaas-timing-guard");
    }

    public TokenPair login(String identifier, String password, String deviceId) {
        AuthUser user = users.findByIdentifier(identifier.trim()).orElse(null);
        if (user == null) {
            passwords.matches(password, dummyHash);
            throw unauthenticated();
        }
        Instant now = clock.instant();
        if (user.lockedUntil() != null && user.lockedUntil().isAfter(now)) {
            throw new ApiException(ErrorCode.RATE_LIMITED, "Too many failed sign-in attempts. Try again later.");
        }
        if (!passwordMatches(password, user.passwordHash())) {
            recordFailure(user);
            throw unauthenticated();
        }
        if (!"ACTIVE".equals(user.status())) {
            throw unauthenticated();
        }
        assertTenantAllowsLogin(user);
        return tenants.call(user.writeScope(), () -> {
            users.recordSuccessfulLogin(user.id());
            return issueNewFamily(user, deviceId, loginContext(user));
        });
    }

    public TokenPair refresh(String refreshToken, String deviceId) {
        StoredRefreshToken stored = refreshTokens.findByHash(TokenHasher.sha256(refreshToken))
                .orElseThrow(AuthService::unauthenticated);
        Instant now = clock.instant();
        if (stored.revokedAt() != null || stored.expiresAt().isBefore(now)) {
            throw unauthenticated();
        }
        if (stored.deviceId() != null && !stored.deviceId().equals(deviceId)) {
            throw unauthenticated();
        }
        if (stored.usedAt() != null) {
            refreshTokens.revokeFamily(stored.familyId());
            log.warn("Refresh token reuse detected; revoked family {}", stored.familyId());
            throw unauthenticated();
        }
        SessionSnapshot session = users.session(stored.userId()).orElseThrow(AuthService::unauthenticated);
        if (!"ACTIVE".equals(session.userStatus())) {
            throw unauthenticated();
        }
        if ("SUSPENDED".equals(session.tenantStatus()) || "CANCELLED".equals(session.tenantStatus())) {
            String ctx = stored.contextKind();
            boolean business = ctx == null || ctx.isBlank() || "BUSINESS".equals(ctx);
            if (business) {
                throw new ApiException(ErrorCode.TENANT_SUSPENDED, "This business account is suspended.");
            }
        }
        AuthUser user = new AuthUser(
                stored.userId(), session.tenantId(), session.fullName(), "", session.roleCode(),
                session.userStatus(), session.tokenVersion(), 0, null);
        AccessContext access = accessFromStored(stored, user);
        return tenants.call(scopeFor(user.id(), session, access), () -> rotate(stored, user, deviceId, access));
    }

    public void logout(String refreshToken) {
        refreshTokens.findByHash(TokenHasher.sha256(refreshToken))
                .ifPresent(stored -> refreshTokens.revokeFamily(stored.familyId()));
    }

    public String forgotPassword(String identifier) {
        AuthUser user = users.findByIdentifier(identifier.trim()).orElse(null);
        if (user == null || !"ACTIVE".equals(user.status())) {
            return null;
        }
        String raw = TokenHasher.randomUrlToken();
        passwordResets.invalidateUnused(user.id());
        passwordResets.insert(user.id(), TokenHasher.sha256(raw), clock.instant().plus(properties.resetTtl()));
        log.info("Stub email: password reset requested for user {}", user.id());
        return properties.exposeResetToken() ? raw : null;
    }

    public void resetPassword(String resetToken, String newPassword) {
        var token = passwordResets.findActive(TokenHasher.sha256(resetToken))
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED, "Invalid or expired reset token."));
        SessionSnapshot session = users.session(token.userId()).orElseThrow(AuthService::unauthenticated);
        tenants.run(scopeFor(token.userId(), session, loginContext(users.findById(token.userId()).orElseThrow())), () -> {
            users.bumpTokenVersionAndSetPassword(token.userId(), passwords.encode(newPassword));
            passwordResets.markUsed(token.id());
            refreshTokens.revokeAllForUser(token.userId());
        });
    }

    @Transactional
    public void changePassword(String currentPassword, String newPassword) {
        UUID userId = currentUserId();
        AuthUser user = users.findById(userId).orElseThrow(AuthService::unauthenticated);
        if (!passwords.matches(currentPassword, user.passwordHash())) {
            throw unauthenticated();
        }
        users.bumpTokenVersionAndSetPassword(userId, passwords.encode(newPassword));
        refreshTokens.revokeAllForUser(userId);
    }

    @Transactional(readOnly = true)
    public Me me() {
        UUID userId = currentUserId();
        AuthUser user = users.findById(userId).orElseThrow(AuthService::unauthenticated);
        return new Me(user.id(), user.fullName(), user.roleCode(), user.tenantId());
    }

    public TokenPair issueSession(AuthUser user, String deviceId, AccessContext ctx) {
        return tenants.call(scopeForUser(user, ctx), () -> issueNewFamily(user, deviceId, ctx));
    }

    public TokenPair switchContext(String kind, UUID tenantId, UUID workspaceId, UUID groupId, String deviceId) {
        AuthUser user = users.findById(currentUserId()).orElseThrow(AuthService::unauthenticated);
        AccessContext ctx = switch (kind == null ? "" : kind.toUpperCase()) {
            case "PERSONAL" -> {
                UUID ws = workspaceId != null ? workspaceId : identity.personalWorkspaceId(user.id()).orElseThrow(
                        () -> new ApiException(ErrorCode.NOT_FOUND, "Personal workspace not found."));
                if (!identity.personalWorkspaceId(user.id()).map(ws::equals).orElse(false)) {
                    throw new ApiException(ErrorCode.FORBIDDEN, "You cannot open that workspace.");
                }
                yield AccessContext.personal(ws);
            }
            case "GROUP" -> {
                if (groupId == null) {
                    throw new ApiException(ErrorCode.FORBIDDEN, "You are not in that group.");
                }
                boolean member = identity.memberships(user.id()).stream()
                        .anyMatch(m -> "GROUP".equals(m.kind()) && groupId.equals(m.groupId()));
                if (!member) {
                    throw new ApiException(ErrorCode.FORBIDDEN, "You are not in that group.");
                }
                yield AccessContext.group(groupId);
            }
            case "BUSINESS" -> {
                UUID tid = tenantId != null ? tenantId : user.tenantId();
                if (tid == null || user.tenantId() == null || !tid.equals(user.tenantId())) {
                    throw new ApiException(ErrorCode.FORBIDDEN, "You cannot open that business.");
                }
                yield AccessContext.business(tid);
            }
            default -> throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown context.");
        };
        identity.upsertProfileDefault(user.id(), ctx.kind(), ctx.tenantId(), ctx.workspaceId(), ctx.groupId());
        return issueSession(user, deviceId == null || deviceId.isBlank() ? "web" : deviceId, ctx);
    }

    private TokenPair issueNewFamily(AuthUser user, String deviceId, AccessContext ctx) {
        UUID familyId = UUID.randomUUID();
        String rawRefresh = TokenHasher.randomUrlToken();
        UUID jwtTenant = "BUSINESS".equals(ctx.kind()) ? ctx.tenantId() : null;
        refreshTokens.insert(
                user.id(), jwtTenant, familyId, TokenHasher.sha256(rawRefresh), deviceId,
                clock.instant().plus(properties.refreshTtl()),
                ctx.kind(), ctx.workspaceId(), ctx.groupId());
        String access = jwt.issueAccessToken(
                user.id(), jwtTenant, user.roleCode(), user.tokenVersion(),
                null, properties.accessTtl(), ctx.kind(), ctx.workspaceId(), ctx.groupId());
        return new TokenPair(access, rawRefresh, jwt.accessExpiresInSeconds(), user);
    }

    private TokenPair rotate(StoredRefreshToken previous, AuthUser user, String deviceId, AccessContext ctx) {
        String rawRefresh = TokenHasher.randomUrlToken();
        UUID jwtTenant = "BUSINESS".equals(ctx.kind()) ? ctx.tenantId() : null;
        UUID newId = refreshTokens.insertReturningId(
                user.id(), jwtTenant, previous.familyId(), TokenHasher.sha256(rawRefresh), deviceId,
                clock.instant().plus(properties.refreshTtl()),
                ctx.kind(), ctx.workspaceId(), ctx.groupId());
        refreshTokens.markUsed(previous.id(), newId);
        String access = jwt.issueAccessToken(
                user.id(), jwtTenant, user.roleCode(), user.tokenVersion(),
                null, properties.accessTtl(), ctx.kind(), ctx.workspaceId(), ctx.groupId());
        return new TokenPair(access, rawRefresh, jwt.accessExpiresInSeconds(), user);
    }

    private void recordFailure(AuthUser user) {
        int fails = user.failedLoginCount() + 1;
        Instant locked = fails >= properties.maxFailedLogins()
                ? clock.instant().plus(properties.lockout())
                : user.lockedUntil();
        tenants.run(user.writeScope(), () -> users.recordFailedLogin(user.id(), fails, locked));
    }

    private void assertTenantAllowsLogin(AuthUser user) {
        if (user.tenantId() == null) {
            return;
        }
        String status = tenants.call(user.writeScope(), () -> users.tenantStatus(user.tenantId()).orElse(""));
        if ("SUSPENDED".equals(status) || "CANCELLED".equals(status)) {
            throw new ApiException(ErrorCode.TENANT_SUSPENDED, "This business account is suspended.");
        }
    }

    private AccessContext loginContext(AuthUser user) {
        if (user.platform()) {
            return AccessContext.platform();
        }
        var profile = identity.profile(user.id());
        if (profile.isPresent() && "PERSONAL".equals(profile.get().defaultKind()) && profile.get().defaultWorkspaceId() != null) {
            return AccessContext.personal(profile.get().defaultWorkspaceId());
        }
        if (profile.isPresent() && "GROUP".equals(profile.get().defaultKind()) && profile.get().defaultGroupId() != null) {
            return AccessContext.group(profile.get().defaultGroupId());
        }
        if (user.tenantId() != null) {
            return AccessContext.business(user.tenantId());
        }
        UUID ws = identity.personalWorkspaceId(user.id()).orElse(null);
        return AccessContext.personal(ws);
    }

    private static AccessContext accessFromStored(StoredRefreshToken stored, AuthUser user) {
        String kind = stored.contextKind() == null || stored.contextKind().isBlank()
                ? (user.tenantId() != null ? "BUSINESS" : "PERSONAL")
                : stored.contextKind();
        return switch (kind) {
            case "PERSONAL" -> AccessContext.personal(stored.workspaceId());
            case "GROUP" -> AccessContext.group(stored.groupId());
            case "PLATFORM" -> AccessContext.platform();
            default -> AccessContext.business(stored.tenantId() != null ? stored.tenantId() : user.tenantId());
        };
    }

    private static TenantScope scopeFor(UUID userId, SessionSnapshot session, AccessContext ctx) {
        AuthUser synthetic = new AuthUser(userId, session.tenantId(), session.fullName(), "", session.roleCode(),
                session.userStatus(), session.tokenVersion(), 0, null);
        return scopeForUser(synthetic, ctx);
    }

    private static TenantScope scopeForUser(AuthUser user, AccessContext ctx) {
        if (user.platform() || "PLATFORM".equals(ctx.kind())) {
            return TenantScope.platformAdmin().withUser(user.id());
        }
        return switch (ctx.kind()) {
            case "PERSONAL" -> TenantScope.personal(user.id(), ctx.workspaceId());
            case "GROUP" -> TenantScope.group(user.id(), ctx.groupId());
            default -> TenantScope.tenant(ctx.tenantId() != null ? ctx.tenantId() : user.tenantId()).withUser(user.id());
        };
    }

    private static UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwt) || !auth.isAuthenticated()) {
            throw unauthenticated();
        }
        return UUID.fromString(jwt.getToken().getSubject());
    }

    private boolean passwordMatches(String raw, String hash) {
        try {
            return hash != null && passwords.matches(raw, hash);
        } catch (RuntimeException e) {
            log.warn("Stored password hash could not be verified");
            return false;
        }
    }

    private static ApiException unauthenticated() {
        return new ApiException(ErrorCode.UNAUTHENTICATED, GENERIC_AUTH);
    }

    public TokenPair impersonate(AuthUser user, UUID impersonatorId) {
        String access = jwt.issueAccessToken(
                user.id(),
                user.tenantId(),
                user.roleCode(),
                user.tokenVersion(),
                impersonatorId,
                java.time.Duration.ofHours(2));
        return new TokenPair(access, "", jwt.accessExpiresInSeconds() * 8, user);
    }

    public record TokenPair(String accessToken, String refreshToken, long expiresIn, AuthUser user) {}

    public record Me(UUID id, String fullName, String role, UUID tenantId) {}
}
