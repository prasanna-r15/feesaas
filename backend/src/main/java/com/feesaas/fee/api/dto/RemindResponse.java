package com.feesaas.fee.api.dto;

public record RemindResponse(
        String channel,
        String body,
        String waLink,
        String smsLink,
        String tenantWhatsapp,
        String tenantPhone
) {}
