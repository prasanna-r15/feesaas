package com.feesaas.tenant.application;

import com.feesaas.shared.error.ApiException;
import com.feesaas.shared.error.ErrorCode;
import java.util.Locale;

public final class TenantSlugs {

    private TenantSlugs() {}

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "URL slug is required. Use lowercase letters, numbers, and hyphens.");
        }
        String slug = raw.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");
        if (slug.length() > 63) {
            slug = slug.substring(0, 63).replaceAll("-+$", "");
        }
        if (slug.length() < 2) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "URL slug must be at least 2 characters (lowercase letters, numbers, hyphens).");
        }
        return slug;
    }
}
