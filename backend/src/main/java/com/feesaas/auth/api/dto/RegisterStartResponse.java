package com.feesaas.auth.api.dto;

import java.util.UUID;

public record RegisterStartResponse(UUID challengeId, String channel, String destination, String otp) {}
