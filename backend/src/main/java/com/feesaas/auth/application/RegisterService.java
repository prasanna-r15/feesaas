package com.feesaas.auth.application;

import com.feesaas.auth.api.dto.RegisterStartResponse;
import com.feesaas.auth.domain.AuthUser;
import com.feesaas.auth.infra.AuthUserRepository;
import com.feesaas.auth.infra.IdentityRepository;
import com.feesaas.auth.infra.SignupOtpRepository;
import com.feesaas.auth.infra.SignupOtpRepository.Challenge;
import com.feesaas.notify.infra.PlatformMailSettingsRepository;
import com.feesaas.shared.error.ApiException;
import com.feesaas.shared.error.ErrorCode;
import com.feesaas.shared.security.AuthProperties;
import com.feesaas.shared.security.TokenHasher;
import com.github.f4b6a3.uuid.UuidCreator;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class RegisterService {

    private final IdentityRepository identity;
    private final AuthUserRepository users;
    private final SignupOtpRepository challenges;
    private final PasswordEncoder passwords;
    private final AuthService auth;
    private final AuthProperties properties;
    private final PlatformMailSettingsRepository mailSettings;
    private final SignupOtpSender sender;
    private final Clock clock;

    public RegisterService(
            IdentityRepository identity,
            AuthUserRepository users,
            SignupOtpRepository challenges,
            PasswordEncoder passwords,
            AuthService auth,
            AuthProperties properties,
            PlatformMailSettingsRepository mailSettings,
            SignupOtpSender sender,
            Clock clock) {
        this.identity = identity;
        this.users = users;
        this.challenges = challenges;
        this.passwords = passwords;
        this.auth = auth;
        this.properties = properties;
        this.mailSettings = mailSettings;
        this.sender = sender;
        this.clock = clock;
    }

    public RegisterStartResponse startWithPassword(String fullName, String email, String phone, String password) {
        String normalisedEmail = blankToNull(email == null ? null : email.trim().toLowerCase());
        String normalisedPhone = blankToNull(phone);
        if (fullName == null || fullName.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Name is required.");
        }
        if (normalisedEmail == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Email is required.");
        }
        if (normalisedPhone == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Phone is required.");
        }
        if (taken(normalisedEmail) || taken(normalisedPhone)) {
            throw new ApiException(ErrorCode.CONFLICT, "An account with this email or phone already exists.");
        }
        String otp = TokenHasher.numericOtp(6);
        UUID id = UuidCreator.getTimeOrderedEpoch();
        Instant expires = clock.instant().plus(properties.otpTtl());
        challenges.insert(
                id,
                fullName.trim(),
                normalisedEmail,
                normalisedPhone,
                passwords.encode(password),
                TokenHasher.sha256(otp),
                "EMAIL",
                expires);
        if (!properties.exposeOtp()) {
            sender.sendEmail(mailSettings.load(), normalisedEmail, fullName.trim(), otp);
        }
        return new RegisterStartResponse(
                id,
                "EMAIL",
                maskEmail(normalisedEmail),
                properties.exposeOtp() ? otp : null);
    }

    public AuthService.TokenPair verify(String challengeId, String otp, String deviceId) {
        UUID id;
        try {
            id = UUID.fromString(challengeId);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Invalid verification request.");
        }
        Challenge row = challenges.find(id).orElseThrow(() ->
                new ApiException(ErrorCode.VALIDATION_FAILED, "That code has expired. Start signup again."));
        if (row.usedAt() != null || row.expiresAt().isBefore(clock.instant())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "That code has expired. Start signup again.");
        }
        if (row.attempts() >= 5) {
            throw new ApiException(ErrorCode.RATE_LIMITED, "Too many attempts. Start signup again.");
        }
        if (!TokenHasher.sha256(otp.trim()).equals(row.otpHash())) {
            challenges.bumpAttempts(id);
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "That code is incorrect.");
        }
        challenges.markUsed(id);
        try {
            var userId = identity.registerIndividual(row.fullName(), row.email(), row.phone(), row.passwordHash());
            var workspaceId = identity.personalWorkspaceId(userId)
                    .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR, "Could not create workspace."));
            identity.seedCategories(workspaceId);
            AuthUser user = users.findByIdentifier(row.email())
                    .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR, "Could not load new account."));
            return auth.issueSession(user, deviceId == null || deviceId.isBlank() ? "web" : deviceId,
                    AccessContext.personal(workspaceId));
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(ErrorCode.CONFLICT, "An account with this email or phone already exists.");
        }
    }

    private boolean taken(String identifier) {
        return users.findByIdentifier(identifier).isPresent();
    }

    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + email.substring(Math.max(at, 0));
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
