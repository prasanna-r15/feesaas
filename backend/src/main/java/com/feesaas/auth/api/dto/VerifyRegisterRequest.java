package com.feesaas.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerifyRegisterRequest(
        @NotBlank String challengeId,
        @NotBlank @Size(min = 4, max = 8) String otp,
        @Size(max = 128) String deviceId
) {}
