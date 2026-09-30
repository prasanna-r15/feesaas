package com.feesaas.tenant.api.dto;

import jakarta.validation.constraints.Size;

public record PatchTenantRequest(
        @Size(max = 120) String name,
        @Size(max = 120) String slug,
        String timezone,
        @Size(min = 3, max = 3) String currency,
        @Size(max = 1_600_000) String logoBase64,
        Boolean clearLogo,
        @Size(max = 120) String displayName,
        @Size(max = 7) String accentColor,
        java.time.Instant trialEndsAt,
        Integer graceDays,
        String billingStatus,
        Integer customMaxMembers,
        Integer customMaxStaff,
        Boolean clearCustomLimits,
        @Size(max = 32) String phone,
        @Size(max = 32) String whatsappNumber
) {}
