package com.feesaas.personal.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCategoryRequest(
        @NotBlank String name,
        String icon,
        String color
) {}
