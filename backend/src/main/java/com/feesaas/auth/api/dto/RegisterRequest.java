package com.feesaas.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 120) String fullName,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 32) String phone,
        @NotBlank @Size(min = 8, max = 128) String password,
        @Size(max = 128) String deviceId
) {}
