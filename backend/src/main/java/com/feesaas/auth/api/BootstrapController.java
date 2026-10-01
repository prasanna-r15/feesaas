package com.feesaas.auth.api;

import com.feesaas.auth.api.dto.BootstrapResponse;
import com.feesaas.auth.api.dto.SwitchContextRequest;
import com.feesaas.auth.api.dto.TokenResponse;
import com.feesaas.auth.application.AuthService;
import com.feesaas.auth.application.AuthService.TokenPair;
import com.feesaas.auth.application.BootstrapService;
import com.feesaas.auth.application.BootstrapService.Bootstrap;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class BootstrapController {

    private final BootstrapService bootstrap;
    private final AuthService auth;

    public BootstrapController(BootstrapService bootstrap, AuthService auth) {
        this.bootstrap = bootstrap;
        this.auth = auth;
    }

    @GetMapping("/bootstrap")
    public BootstrapResponse bootstrap() {
        Bootstrap data = bootstrap.load();
        var user = data.user();
        var tenant = data.tenant();
        return new BootstrapResponse(
                new BootstrapResponse.User(user.id(), user.fullName(), user.role(), user.tenantId()),
                data.permissions(),
                tenant == null ? null : new BootstrapResponse.Tenant(
                        tenant.id(), tenant.name(), tenant.slug(), tenant.businessType(), tenant.status(),
                        tenant.timezone(), tenant.currency(), tenant.labels(), tenant.modules(),
                        tenant.logoBase64(), tenant.displayName(), tenant.accentColor()),
                data.dashboard().stream()
                        .map(w -> new BootstrapResponse.DashboardWidget(w.id(), w.module()))
                        .toList(),
                data.contexts().stream()
                        .map(c -> new BootstrapResponse.UserContext(
                                c.kind(), c.role(), c.tenantId(), c.workspaceId(), c.groupId(), c.label()))
                        .toList(),
                data.activeContext() == null ? null : new BootstrapResponse.UserContext(
                        data.activeContext().kind(), data.activeContext().role(), data.activeContext().tenantId(),
                        data.activeContext().workspaceId(), data.activeContext().groupId(), data.activeContext().label()),
                data.needsOnboarding());
    }

    @PostMapping("/context")
    public TokenResponse switchContext(@Valid @RequestBody SwitchContextRequest request) {
        TokenPair pair = auth.switchContext(
                request.kind(), request.tenantId(), request.workspaceId(), request.groupId(), request.deviceId());
        var user = pair.user();
        return new TokenResponse(
                pair.accessToken(),
                pair.refreshToken(),
                "Bearer",
                pair.expiresIn(),
                new TokenResponse.UserSummary(user.id(), user.fullName(), user.roleCode(), user.tenantId()));
    }

    @PostMapping("/onboarding/complete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void completeOnboarding() {
        bootstrap.completeOnboarding();
    }
}
