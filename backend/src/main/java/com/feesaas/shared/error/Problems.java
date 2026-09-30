package com.feesaas.shared.error;

import java.net.URI;
import org.slf4j.MDC;
import org.springframework.http.ProblemDetail;

public final class Problems {
    private Problems() {}

    public static ProblemDetail of(ErrorCode code, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(code.status(), detail);
        pd.setType(URI.create("urn:feesaas:error:" + code.name().toLowerCase()));
        pd.setTitle(code.name());
        pd.setProperty("code", code.name());
        String requestId = MDC.get(RequestIdFilterKeys.MDC_KEY);
        if (requestId != null) {
            pd.setProperty("requestId", requestId);
        }
        return pd;
    }
}
