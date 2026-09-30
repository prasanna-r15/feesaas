package com.feesaas.configuration.api;

import com.feesaas.configuration.api.dto.TenantContactRequest;
import com.feesaas.configuration.api.dto.TenantContactResponse;
import com.feesaas.shared.error.ApiException;
import com.feesaas.shared.error.ErrorCode;
import com.feesaas.shared.tenancy.TenantContext;
import com.feesaas.tenant.infra.TenantRepository;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settings/contact")
public class TenantContactController {

    private final TenantRepository tenants;

    public TenantContactController(TenantRepository tenants) {
        this.tenants = tenants;
    }

    @GetMapping
    @PreAuthorize("hasPermission(null, 'reminders.send') or hasPermission(null, 'settings.manage')")
    public TenantContactResponse get() {
        var row = tenants.findContact(TenantContext.requireTenantId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Business profile not found."));
        return new TenantContactResponse(row.name(), row.phone(), row.whatsappNumber());
    }

    @PutMapping
    @PreAuthorize("hasPermission(null, 'settings.manage')")
    public TenantContactResponse put(@Valid @RequestBody TenantContactRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        tenants.updateContact(tenantId, blank(request.phone()), blank(request.whatsappNumber()));
        return get();
    }

    private static String blank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
