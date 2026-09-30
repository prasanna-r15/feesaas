package com.feesaas.personal.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record UpsertBudgetRequest(
        @NotNull UUID categoryId,
        String yearMonth,
        @NotNull @Positive Long limitMinor
) {}
