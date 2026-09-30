package com.feesaas.personal.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.UUID;

public record CreateIncomeRequest(
        @NotNull @Positive Long amountMinor,
        @NotBlank String source,
        String description,
        LocalDate occurredOn,
        UUID clientId
) {}
