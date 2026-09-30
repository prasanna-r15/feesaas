package com.feesaas.shared.security;

import com.feesaas.shared.tenancy.TenantClaims;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class JwtService {

    private final JwtKeyHolder keys;
    private final AuthProperties properties;
    private final Clock clock;

    public JwtService(JwtKeyHolder keys, AuthProperties properties, Clock clock) {
        this.keys = keys;
        this.properties = properties;
        this.clock = clock;
    }

    public String issueAccessToken(UUID userId, UUID tenantId, String role, int tokenVersion) {
        String ctx = TenantClaims.PLATFORM_ROLE.equals(role)
                ? "PLATFORM"
                : (tenantId != null ? "BUSINESS" : "PERSONAL");
        return issueAccessToken(userId, tenantId, role, tokenVersion, null, properties.accessTtl(), ctx, null, null);
    }

    public String issueAccessToken(
            UUID userId,
            UUID tenantId,
            String role,
            int tokenVersion,
            UUID impersonatorId,
            java.time.Duration ttl) {
        return issueAccessToken(userId, tenantId, role, tokenVersion, impersonatorId, ttl, "BUSINESS", null, null);
    }

    public String issueAccessToken(
            UUID userId,
            UUID tenantId,
            String role,
            int tokenVersion,
            UUID impersonatorId,
            java.time.Duration ttl,
            String ctx,
            UUID workspaceId,
            UUID groupId) {
        Instant now = clock.instant();
        Instant exp = now.plus(ttl == null ? properties.accessTtl() : ttl);
        try {
            JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                    .subject(userId.toString())
                    .issuer(properties.issuer())
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(exp))
                    .jwtID(UUID.randomUUID().toString())
                    .claim(TenantClaims.ROLE, role)
                    .claim(TenantClaims.TOKEN_VERSION, tokenVersion);
            if (ctx != null && !ctx.isBlank()) {
                claims.claim(TenantClaims.CTX, ctx);
            }
            if (tenantId != null) {
                claims.claim(TenantClaims.TENANT_ID, tenantId.toString());
            }
            if (workspaceId != null) {
                claims.claim(TenantClaims.WORKSPACE_ID, workspaceId.toString());
            }
            if (groupId != null) {
                claims.claim(TenantClaims.GROUP_ID, groupId.toString());
            }
            if (impersonatorId != null) {
                claims.claim(TenantClaims.IMPERSONATOR, impersonatorId.toString());
            }
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256)
                            .keyID(keys.kid())
                            .type(JOSEObjectType.JWT)
                            .build(),
                    claims.build());
            jwt.sign(new RSASSASigner(keys.privateKey()));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Could not sign access token", e);
        }
    }

    public long accessExpiresInSeconds() {
        return properties.accessTtl().toSeconds();
    }
}
