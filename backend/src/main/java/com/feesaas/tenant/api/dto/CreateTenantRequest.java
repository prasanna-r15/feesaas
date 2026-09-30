package com.feesaas.tenant.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateTenantRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 120) String slug,
        @NotBlank String businessType,
        String timezone,
        @Size(min = 3, max = 3) String currency,
        @Size(max = 1_600_000) String logoBase64,
        @NotNull @Valid OwnerRequest owner
) {
    public record OwnerRequest(
            @NotBlank @Size(max = 120) String fullName,
            @Email String email,
            @Size(max = 32) String phone,
            @NotBlank @Size(min = 8, max = 128) String password
    ) {}
}
