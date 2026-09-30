package com.feesaas.personal.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.UUID;

public record CreateExpenseRequest(
        @NotNull @Positive Long amountMinor,
        @NotNull UUID categoryId,
        String description,
        LocalDate occurredOn,
        String method,
        UUID clientId
) {}
