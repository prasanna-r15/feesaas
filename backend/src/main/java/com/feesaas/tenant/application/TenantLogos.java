package com.feesaas.tenant.application;

import com.feesaas.shared.error.ApiException;
import com.feesaas.shared.error.ErrorCode;
import java.util.Base64;
import java.util.Locale;

final class TenantLogos {

    static final int MAX_BYTES = 1_048_576;

    private TenantLogos() {}

    static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        String mime = "image/png";
        String payload = value;
        int comma = value.indexOf(',');
        if (value.toLowerCase(Locale.ROOT).startsWith("data:") && comma > 0) {
            String header = value.substring(5, comma).toLowerCase(Locale.ROOT);
            payload = value.substring(comma + 1);
            if (header.contains("image/jpeg") || header.contains("image/jpg")) {
                mime = "image/jpeg";
            } else if (header.contains("image/webp")) {
                mime = "image/webp";
            } else if (header.contains("image/gif")) {
                mime = "image/gif";
            } else if (header.contains("image/png")) {
                mime = "image/png";
            } else {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Logo must be PNG, JPEG, WebP, or GIF.");
            }
        }
        payload = payload.replaceAll("\\s", "");
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(payload);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Logo is not valid base64.");
        }
        if (decoded.length == 0) {
            return null;
        }
        if (decoded.length > MAX_BYTES) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Logo must be smaller than 1 MB.");
        }
        mime = detectMime(decoded, mime);
        return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(decoded);
    }

    private static String detectMime(byte[] bytes, String fallback) {
        if (bytes.length >= 8
                && bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47) {
            return "image/png";
        }
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8) {
            return "image/jpeg";
        }
        if (bytes.length >= 6 && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F') {
            return "image/gif";
        }
        if (bytes.length >= 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "image/webp";
        }
        if (fallback.startsWith("image/")) {
            return fallback;
        }
        throw new ApiException(ErrorCode.VALIDATION_FAILED, "Logo must be PNG, JPEG, WebP, or GIF.");
    }
}
