package com.feesaas.shared.tenancy;

import com.feesaas.shared.error.ApiException;
import com.feesaas.shared.error.ErrorCode;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Holds the current request's scope. Populated ONLY from the authenticated principal
 * (see TenantContextFilter) or programmatically for background jobs (see TenantExecutor).
 * Nothing that comes from a request body, query string or header ever reaches this class.
 */
public final class TenantContext {

    private static final ThreadLocal<TenantScope> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static Optional<TenantScope> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static UUID requireTenantId() {
        return current()
                .map(TenantScope::tenantId)
                .orElseThrow(() -> new ApiException(ErrorCode.FORBIDDEN, "No tenant in context."));
    }

    public static UUID requireUserId() {
        return current()
                .map(TenantScope::userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED, "Invalid credentials."));
    }

    static void set(TenantScope scope) { CURRENT.set(scope); }

    static void clear() { CURRENT.remove(); }

    /** Runs work with the given scope and restores the previous scope afterwards. */
    public static <T> T callAs(TenantScope scope, Supplier<T> work) {
        TenantScope previous = CURRENT.get();
        CURRENT.set(scope);
        try {
            return work.get();
        } finally {
            if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
        }
    }
}
