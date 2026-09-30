package com.feesaas.user.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ReplacePermissionsRequest(@NotNull List<String> permissions) {}
