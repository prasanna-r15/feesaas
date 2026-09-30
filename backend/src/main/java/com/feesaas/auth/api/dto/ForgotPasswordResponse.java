package com.feesaas.auth.api.dto;

public record ForgotPasswordResponse(boolean accepted, String resetToken) {}
