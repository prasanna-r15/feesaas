package com.feesaas.shared.web;

import com.feesaas.shared.error.RequestIdFilterKeys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Correlates logs, errors and audit rows. Client-supplied ids are accepted only if they look safe. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    private static final Pattern SAFE = Pattern.compile("^[A-Za-z0-9-]{8,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String incoming = req.getHeader(RequestIdFilterKeys.HEADER);
        String id = (incoming != null && SAFE.matcher(incoming).matches()) ? incoming : UUID.randomUUID().toString();
        MDC.put(RequestIdFilterKeys.MDC_KEY, id);
        res.setHeader(RequestIdFilterKeys.HEADER, id);
        try {
            chain.doFilter(req, res);
        } finally {
            MDC.remove(RequestIdFilterKeys.MDC_KEY);
        }
    }
}
