package com.feesaas.shared.error;

import java.util.Map;

/** Business/API error that maps to an RFC 7807 problem response. */
public class ApiException extends RuntimeException {
    private final ErrorCode code;
    private final transient Map<String, Object> details;

    public ApiException(ErrorCode code, String message) {
        this(code, message, Map.of());
    }

    public ApiException(ErrorCode code, String message, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.details = details;
    }

    public ErrorCode code() { return code; }
    public Map<String, Object> details() { return details; }
}
