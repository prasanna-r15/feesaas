package com.feesaas.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ResetPasswordRequest(
        UUID challengeId,
        @NotBlank String resetToken,
        @NotBlank @Size(min = 8, max = 128) String newPassword
) {}
