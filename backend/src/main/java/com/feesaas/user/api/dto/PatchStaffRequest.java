package com.feesaas.user.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PatchStaffRequest(
        @Size(max = 120) String fullName,
        @Email String email,
        @Size(max = 32) String phone,
        @Pattern(regexp = "ACTIVE|DISABLED") String status
) {}
