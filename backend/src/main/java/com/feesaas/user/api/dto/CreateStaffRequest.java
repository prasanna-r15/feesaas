package com.feesaas.user.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateStaffRequest(
        @NotBlank @Size(max = 120) String fullName,
        @Email String email,
        @Size(max = 32) String phone,
        @Size(min = 8, max = 128) String password,
        List<String> permissions
) {}
