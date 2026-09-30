package com.feesaas.tenant.api;

import com.feesaas.auth.api.dto.TokenResponse;
import com.feesaas.auth.application.AuthService.TokenPair;
import com.feesaas.customer.api.dto.CustomerResponse;
import com.feesaas.tenant.api.dto.CreateTenantRequest;
import com.feesaas.tenant.api.dto.PatchTenantRequest;
import com.feesaas.tenant.api.dto.TenantResponse;
import com.feesaas.tenant.application.PlatformOpsService;
import com.feesaas.tenant.application.TenantSlugs;
import com.feesaas.tenant.application.TenantService;
import com.feesaas.tenant.application.TenantService.CreateTenantCommand;
import com.feesaas.tenant.application.TenantService.PatchTenantCommand;
import com.feesaas.tenant.application.TenantService.TenantView;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/tenants")
public class PlatformTenantController {

    private final TenantService tenants;
    private final PlatformOpsService ops;

    public PlatformTenantController(TenantService tenants, PlatformOpsService ops) {
        this.tenants = tenants;
        this.ops = ops;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TenantResponse create(@Valid @RequestBody CreateTenantRequest request) {
        var owner = request.owner();
        if (owner == null || (owner.email() == null && owner.phone() == null)) {
            throw new com.feesaas.shared.error.ApiException(
                    com.feesaas.shared.error.ErrorCode.VALIDATION_FAILED, "Owner email or phone is required.");
        }
        String timezone = request.timezone() == null || request.timezone().isBlank() ? "Asia/Kolkata" : request.timezone();
        String currency = request.currency() == null || request.currency().isBlank() ? "INR" : request.currency().toUpperCase();
        return toResponse(tenants.create(new CreateTenantCommand(
                request.name().trim(),
                TenantSlugs.normalize(request.slug()),
                request.businessType().trim().toUpperCase(),
                timezone,
                currency,
                owner.fullName().trim(),
                owner.email(),
                owner.phone(),
                owner.password(),
                request.logoBase64())));
    }

    @GetMapping
    public List<TenantResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String plan,
            @RequestParam(required = false) String businessType) {
        return tenants.list(q, status, plan, businessType).stream().map(PlatformTenantController::toResponse).toList();
    }

    @GetMapping("/{id}")
    public TenantResponse get(@PathVariable UUID id) {
        return toResponse(tenants.get(id));
    }

    @PatchMapping("/{id}")
    public TenantResponse patch(@PathVariable UUID id, @Valid @RequestBody PatchTenantRequest request) {
        String slug = request.slug() == null ? null : TenantSlugs.normalize(request.slug());
        String currency = request.currency() == null ? null : request.currency().toUpperCase();
        return toResponse(tenants.patch(id, new PatchTenantCommand(
                request.name(), slug, request.timezone(), currency, request.logoBase64(), request.clearLogo(),
                request.displayName(), request.accentColor(), request.trialEndsAt(), request.graceDays(),
                request.billingStatus(), request.customMaxMembers(), request.customMaxStaff(),
                request.clearCustomLimits(), request.phone(), request.whatsappNumber())));
    }

    @PostMapping("/{id}/plan")
    public TenantResponse plan(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        return toResponse(tenants.setPlan(id, body.get("planCode")));
    }

    @PostMapping("/{id}/suspend")
    public TenantResponse suspend(@PathVariable UUID id) {
        return toResponse(tenants.setStatus(id, "SUSPENDED"));
    }

    @PostMapping("/{id}/activate")
    public TenantResponse activate(@PathVariable UUID id) {
        return toResponse(tenants.setStatus(id, "ACTIVE"));
    }

    @PostMapping("/{id}/restore")
    public TenantResponse restore(@PathVariable UUID id) {
        return toResponse(ops.restore(id));
    }

    @DeleteMapping("/{id}")
    public TenantResponse delete(@PathVariable UUID id) {
        return toResponse(tenants.delete(id));
    }

    @PutMapping("/{id}/modules")
    public TenantResponse modules(@PathVariable UUID id, @RequestBody Map<String, List<String>> body) {
        return toResponse(ops.setModules(id, body.getOrDefault("modules", List.of())));
    }

    @GetMapping("/{id}/notes")
    public List<Map<String, Object>> notes(@PathVariable UUID id) {
        return ops.notes(id).stream().map(n -> Map.<String, Object>of(
                "id", n.id(),
                "body", n.body(),
                "author", n.author() == null ? "" : n.author(),
                "createdAt", n.createdAt().toString()
        )).toList();
    }

    @PostMapping("/{id}/notes")
    public List<Map<String, Object>> addNote(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        return ops.addNote(id, body.get("body")).stream().map(n -> Map.<String, Object>of(
                "id", n.id(),
                "body", n.body(),
                "author", n.author() == null ? "" : n.author(),
                "createdAt", n.createdAt().toString()
        )).toList();
    }

    @GetMapping("/{id}/invoices")
    public List<Map<String, Object>> invoices(@PathVariable UUID id) {
        return ops.invoices(id).stream().map(PlatformTenantController::invoice).toList();
    }

    @PostMapping("/{id}/invoices")
    public List<Map<String, Object>> addInvoice(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        String period = String.valueOf(body.getOrDefault("periodLabel", ""));
        long amount = ((Number) body.getOrDefault("amountMinor", 0)).longValue();
        String currency = body.get("currency") == null ? "INR" : String.valueOf(body.get("currency"));
        LocalDate due = body.get("dueOn") == null || String.valueOf(body.get("dueOn")).isBlank()
                ? null : LocalDate.parse(String.valueOf(body.get("dueOn")));
        String note = body.get("note") == null ? null : String.valueOf(body.get("note"));
        return ops.addInvoice(id, period, amount, currency, due, note).stream()
                .map(PlatformTenantController::invoice).toList();
    }

    @PostMapping("/{id}/invoices/{invoiceId}")
    public List<Map<String, Object>> markInvoice(
            @PathVariable UUID id,
            @PathVariable UUID invoiceId,
            @RequestBody Map<String, String> body) {
        return ops.markInvoice(id, invoiceId, body.get("status")).stream()
                .map(PlatformTenantController::invoice).toList();
    }

    @GetMapping("/{id}/audit")
    public List<Map<String, Object>> audit(@PathVariable UUID id) {
        return ops.audit(id).stream().map(a -> Map.<String, Object>of(
                "id", a.id(),
                "action", a.action(),
                "entityType", a.entityType(),
                "createdAt", a.createdAt().toString()
        )).toList();
    }

    @PatchMapping("/{id}/owner")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void owner(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        ops.updateOwner(id, body.get("fullName"), body.get("email"), body.get("phone"));
    }

    @PostMapping("/{id}/owner/password")
    public Map<String, String> ownerPassword(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        String password = body.get("password");
        if (password == null || password.isBlank()) {
            password = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "Aa1!";
        }
        return Map.of("password", ops.resetOwnerPassword(id, password));
    }

    @PostMapping("/{id}/impersonate")
    public TokenResponse impersonate(@PathVariable UUID id) {
        TokenPair pair = ops.impersonate(id);
        var user = pair.user();
        return new TokenResponse(
                pair.accessToken(),
                pair.refreshToken(),
                "Bearer",
                pair.expiresIn(),
                new TokenResponse.UserSummary(user.id(), user.fullName(), user.roleCode(), user.tenantId()));
    }

    @GetMapping("/{id}/members")
    public List<CustomerResponse> members(@PathVariable UUID id, @RequestParam(required = false) String q) {
        return ops.members(id, q).stream()
                .map(c -> new CustomerResponse(
                        c.id(), c.customerCode(), c.fullName(), c.phone(), c.email(), c.status(),
                        c.notes(), c.dueDate(), c.createdAt(), c.hasWhatsapp(), c.feePlanId(), c.feePlanName(), c.branchId(), c.branchName()))
                .toList();
    }

    private static Map<String, Object> invoice(com.feesaas.tenant.infra.PlatformOpsRepository.InvoiceRow i) {
        return Map.of(
                "id", i.id(),
                "periodLabel", i.periodLabel(),
                "amountMinor", i.amountMinor(),
                "currency", i.currency(),
                "status", i.status(),
                "dueOn", i.dueOn() == null ? "" : i.dueOn().toString(),
                "paidAt", i.paidAt() == null ? "" : i.paidAt().toString(),
                "note", i.note() == null ? "" : i.note()
        );
    }

    private static TenantResponse toResponse(TenantView view) {
        return new TenantResponse(
                view.id(), view.name(), view.slug(), view.businessType(), view.status(),
                view.timezone(), view.currency(), view.createdAt(), view.modules(), view.ownerId(),
                view.planCode(), view.maxMembers(), view.maxStaff(),
                view.hasLogo(), view.logoBase64(),
                view.displayName(), view.accentColor(), view.trialEndsAt(), view.graceDays(),
                view.billingStatus(), view.customMaxMembers(), view.customMaxStaff(),
                view.phone(), view.whatsappNumber(), view.memberCount(), view.staffCount(),
                view.lastLoginAt(), view.lastCollectionOn(),
                view.ownerFullName(), view.ownerEmail(), view.ownerPhone());
    }
}
