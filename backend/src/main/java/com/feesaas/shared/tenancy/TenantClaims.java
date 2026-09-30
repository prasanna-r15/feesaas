package com.feesaas.shared.tenancy;

/** JWT claim names that carry identity. Issued by the auth module (step 1.2). */
public final class TenantClaims {
    public static final String TENANT_ID = "tenant_id";
    public static final String ROLE = "role";
    public static final String TOKEN_VERSION = "ver";
    public static final String IMPERSONATOR = "imp";
    public static final String CTX = "ctx";
    public static final String WORKSPACE_ID = "workspace_id";
    public static final String GROUP_ID = "group_id";
    public static final String PLATFORM_ROLE = "PLATFORM_SUPER_ADMIN";
    public static final String INDIVIDUAL_ROLE = "INDIVIDUAL";
    private TenantClaims() {}
}
