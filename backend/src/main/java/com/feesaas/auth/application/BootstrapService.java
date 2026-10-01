package com.feesaas.auth.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feesaas.auth.domain.AuthUser;
import com.feesaas.auth.infra.AuthUserRepository;
import com.feesaas.auth.infra.IdentityRepository;
import com.feesaas.auth.infra.IdentityRepository.ContextRow;
import com.feesaas.shared.error.ApiException;
import com.feesaas.shared.error.ErrorCode;
import com.feesaas.shared.security.CurrentUser;
import com.feesaas.shared.security.PermissionLookup;
import com.feesaas.shared.tenancy.TenantClaims;
import com.feesaas.shared.tenancy.TenantContext;
import com.feesaas.shared.tenancy.TenantExecutor;
import com.feesaas.shared.tenancy.TenantScope;
import com.feesaas.tenant.infra.TenantRepository;
import com.feesaas.tenant.infra.TenantRepository.TenantRow;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BootstrapService {

    private final AuthUserRepository users;
    private final TenantRepository tenants;
    private final PermissionLookup permissionLookup;
    private final IdentityRepository identity;
    private final TenantExecutor executor;
    private final ObjectMapper json;

    public BootstrapService(
            AuthUserRepository users,
            TenantRepository tenants,
            PermissionLookup permissionLookup,
            IdentityRepository identity,
            TenantExecutor executor,
            ObjectMapper json) {
        this.users = users;
        this.tenants = tenants;
        this.permissionLookup = permissionLookup;
        this.identity = identity;
        this.executor = executor;
        this.json = json;
    }

    @Transactional
    public void completeOnboarding() {
        UUID userId = CurrentUser.id();
        identity.provisionHats(userId, null, null);
        identity.completeOnboarding(userId);
    }

    @Transactional
    public Bootstrap load() {
        UUID userId = CurrentUser.id();
        AuthUser user = users.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED, "Invalid credentials."));
        identity.provisionHats(userId, user.tenantId(), user.tenantId() == null ? null : user.roleCode());
        List<String> permissions = new ArrayList<>(permissionLookup.forUser(userId));
        permissions.sort(String::compareTo);
        TenantSnapshot tenant = null;
        if (user.tenantId() != null) {
            tenant = tenantSnapshot(user.tenantId());
        }
        List<UserContext> contexts = new ArrayList<>();
        for (ContextRow row : identity.memberships(userId)) {
            contexts.add(toContext(row));
        }
        if (user.tenantId() != null && contexts.stream().noneMatch(c -> "BUSINESS".equals(c.kind()))) {
            contexts.add(0, new UserContext(
                    "BUSINESS",
                    user.roleCode(),
                    user.tenantId(),
                    null,
                    null,
                    tenant == null ? "Business" : tenant.displayName()));
        }
        UserContext active = activeFromScope(contexts, tenant);
        boolean needsOnboarding = identity.profile(userId)
                .map(p -> !p.onboardingDone())
                .orElse("INDIVIDUAL".equals(user.roleCode()));
        return new Bootstrap(
                new UserSnapshot(user.id(), user.fullName(), user.roleCode(), user.tenantId()),
                permissions,
                tenant,
                dashboardFor(user, tenant, active),
                contexts,
                active,
                needsOnboarding);
    }

    private UserContext activeFromScope(List<UserContext> contexts, TenantSnapshot tenant) {
        var scope = TenantContext.current().orElse(null);
        if (scope != null && scope.personal()) {
            return contexts.stream().filter(c -> "PERSONAL".equals(c.kind())).findFirst()
                    .orElse(new UserContext("PERSONAL", TenantClaims.INDIVIDUAL_ROLE, null, scope.workspaceId(), null, "Personal"));
        }
        if (scope != null && scope.group()) {
            return contexts.stream().filter(c -> "GROUP".equals(c.kind()) && scope.groupId() != null && scope.groupId().equals(c.groupId()))
                    .findFirst()
                    .orElse(new UserContext("GROUP", "MEMBER", null, null, scope.groupId(), "Group"));
        }
        if (tenant != null) {
            return contexts.stream().filter(c -> "BUSINESS".equals(c.kind())).findFirst()
                    .orElse(new UserContext("BUSINESS", "BUSINESS_OWNER", tenant.id(), null, null, tenant.displayName()));
        }
        if (TenantClaims.PLATFORM_ROLE.equals(CurrentUser.role())) {
            return new UserContext("PLATFORM", TenantClaims.PLATFORM_ROLE, null, null, null, "Platform");
        }
        return contexts.isEmpty() ? null : contexts.getFirst();
    }

    private TenantSnapshot tenantSnapshot(UUID tenantId) {
        TenantScope current = TenantContext.current().orElse(TenantScope.NONE);
        if (tenantId.equals(current.tenantId()) || current.platform()) {
            return readTenantSnapshot(tenantId);
        }
        return executor.call(
                TenantScope.tenant(tenantId).withUser(CurrentUser.id()),
                () -> readTenantSnapshot(tenantId));
    }

    private TenantSnapshot readTenantSnapshot(UUID tenantId) {
        TenantRow row = tenants.findById(tenantId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Tenant not found."));
        Map<String, String> labels = parseLabels(tenants.labelsJson(tenantId).orElse("{}"));
        return new TenantSnapshot(
                row.id(), row.name(), row.slug(), row.businessType(), row.status(),
                row.timezone(), row.currency(), labels, tenants.modules(row.id()),
                row.logoBase64(),
                row.displayName() == null ? row.name() : row.displayName(),
                row.accentColor());
    }

    private static UserContext toContext(ContextRow row) {
        String label = switch (row.kind()) {
            case "PERSONAL" -> row.workspaceName() == null ? "Personal" : row.workspaceName();
            case "BUSINESS" -> row.tenantName() == null ? "Business" : row.tenantName();
            case "GROUP" -> row.groupName() == null ? "Group" : row.groupName();
            default -> row.kind();
        };
        return new UserContext(row.kind(), row.roleCode(), row.tenantId(), row.workspaceId(), row.groupId(), label);
    }

    private Map<String, String> parseLabels(String labelsJson) {
        try {
            Map<String, Object> raw = json.readValue(labelsJson, new TypeReference<>() {});
            Map<String, String> labels = new LinkedHashMap<>();
            raw.forEach((k, v) -> labels.put(k, v == null ? "" : String.valueOf(v)));
            return labels;
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static List<DashboardWidget> dashboardFor(AuthUser user, TenantSnapshot tenant, UserContext active) {
        if (TenantClaims.PLATFORM_ROLE.equals(user.roleCode()) && (active == null || "PLATFORM".equals(active.kind()))) {
            return List.of(new DashboardWidget("platformStats", "PLATFORM"));
        }
        if (active != null && "PERSONAL".equals(active.kind())) {
            return List.of(new DashboardWidget("personalHome", "PERSONAL"));
        }
        if (tenant == null) {
            return List.of(new DashboardWidget("personalHome", "PERSONAL"));
        }
        List<DashboardWidget> widgets = new ArrayList<>();
        if (tenant.modules().contains("FEES") || tenant.modules().contains("PAYMENTS")) {
            widgets.add(new DashboardWidget("pendingFees", "FEES"));
        }
        if (tenant.modules().contains("MEMBERSHIP")) {
            widgets.add(new DashboardWidget("expiringMemberships", "MEMBERSHIP"));
        }
        return widgets;
    }

    public record UserSnapshot(UUID id, String fullName, String role, UUID tenantId) {}

    public record TenantSnapshot(
            UUID id,
            String name,
            String slug,
            String businessType,
            String status,
            String timezone,
            String currency,
            Map<String, String> labels,
            List<String> modules,
            String logoBase64,
            String displayName,
            String accentColor
    ) {}

    public record DashboardWidget(String id, String module) {}

    public record UserContext(
            String kind,
            String role,
            UUID tenantId,
            UUID workspaceId,
            UUID groupId,
            String label
    ) {}

    public record Bootstrap(
            UserSnapshot user,
            List<String> permissions,
            TenantSnapshot tenant,
            List<DashboardWidget> dashboard,
            List<UserContext> contexts,
            UserContext activeContext,
            boolean needsOnboarding
    ) {}
}
