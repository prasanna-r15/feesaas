package com.feesaas.auth.api.dto;

import java.util.UUID;

public record ForgotPasswordResponse(boolean accepted, UUID challengeId, String resetToken) {}
