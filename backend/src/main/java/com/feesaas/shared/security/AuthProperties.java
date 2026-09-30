package com.feesaas.shared.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "feesaas.auth")
public record AuthProperties(
        String issuer,
        Duration accessTtl,
        Duration refreshTtl,
        Duration resetTtl,
        Duration lockout,
        int maxFailedLogins,
        boolean exposeResetToken,
        @DefaultValue("10m") Duration otpTtl,
        @DefaultValue("false") boolean exposeOtp
) {}
