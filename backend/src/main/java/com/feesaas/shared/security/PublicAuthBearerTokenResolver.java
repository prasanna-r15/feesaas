package com.feesaas.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Public auth endpoints accept an expired/stale access token in the same request as a refresh
 * token; we ignore the Bearer header there so login/refresh/logout are not blocked.
 */
public class PublicAuthBearerTokenResolver implements BearerTokenResolver {

    private static final List<RequestMatcher> PUBLIC = List.of(
            new AntPathRequestMatcher("/api/v1/auth/register", "POST"),
            new AntPathRequestMatcher("/api/v1/auth/register/verify", "POST"),
            new AntPathRequestMatcher("/api/v1/auth/login", "POST"),
            new AntPathRequestMatcher("/api/v1/auth/refresh", "POST"),
            new AntPathRequestMatcher("/api/v1/auth/logout", "POST"),
            new AntPathRequestMatcher("/api/v1/auth/password/forgot", "POST"),
            new AntPathRequestMatcher("/api/v1/auth/password/reset", "POST"),
            new AntPathRequestMatcher("/actuator/health/**"),
            new AntPathRequestMatcher("/api/health/keep-alive", "GET"),
            new AntPathRequestMatcher("/v3/api-docs/**"),
            new AntPathRequestMatcher("/swagger-ui/**"),
            new AntPathRequestMatcher("/swagger-ui.html")
    );

    private final BearerTokenResolver delegate = new DefaultBearerTokenResolver();

    @Override
    public String resolve(HttpServletRequest request) {
        for (RequestMatcher matcher : PUBLIC) {
            if (matcher.matches(request)) {
                return null;
            }
        }
        return delegate.resolve(request);
    }
}
