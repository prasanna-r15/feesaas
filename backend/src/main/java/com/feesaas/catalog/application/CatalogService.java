package com.feesaas.catalog.application;

import com.feesaas.catalog.infra.CatalogRepository;
import com.feesaas.catalog.infra.CatalogRepository.AddonRow;
import com.feesaas.catalog.infra.CatalogRepository.BranchRow;
import com.feesaas.catalog.infra.CatalogRepository.DietRow;
import com.feesaas.catalog.infra.CatalogRepository.MemberContact;
import com.feesaas.catalog.infra.CatalogRepository.SaleRow;
import com.feesaas.shared.contact.PhoneNumbers;
import com.feesaas.shared.error.ApiException;
import com.feesaas.shared.error.ErrorCode;
import com.feesaas.shared.security.CurrentUser;
import com.feesaas.shared.tenancy.TenantContext;
import com.feesaas.shared.tenancy.TenantExecutor;
import com.feesaas.shared.tenancy.TenantScope;
import com.feesaas.tenant.infra.TenantRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {

    private final CatalogRepository catalog;
    private final TenantRepository tenants;
    private final TenantExecutor executor;

    public CatalogService(CatalogRepository catalog, TenantRepository tenants, TenantExecutor executor) {
        this.catalog = catalog;
        this.tenants = tenants;
        this.executor = executor;
    }

    @PreAuthorize("hasPermission(null, 'customers.view')")
    @Transactional(readOnly = true)
    public List<BranchRow> listBranches() {
        return catalog.listBranches(TenantContext.requireTenantId());
    }

    @PreAuthorize("hasPermission(null, 'settings.manage')")
    @Transactional
    public List<BranchRow> createBranch(String name, String address, String phone, boolean primary) {
        UUID tenantId = TenantContext.requireTenantId();
        catalog.insertBranch(tenantId, requireName(name), blankToNull(address), blankToNull(phone), primary);
        return catalog.listBranches(tenantId);
    }

    @PreAuthorize("hasPermission(null, 'settings.manage')")
    @Transactional
    public List<BranchRow> patchBranch(UUID id, String name, String address, String phone, Boolean primary) {
        UUID tenantId = TenantContext.requireTenantId();
        if (catalog.updateBranch(tenantId, id, blankToNull(name), blankToNull(address), blankToNull(phone), primary) == 0) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Branch not found.");
        }
        return catalog.listBranches(tenantId);
    }

    @PreAuthorize("hasPermission(null, 'settings.manage')")
    @Transactional
    public List<BranchRow> deleteBranch(UUID id) {
        UUID tenantId = TenantContext.requireTenantId();
        if (catalog.softDeleteBranch(tenantId, id) == 0) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Branch not found.");
        }
        return catalog.listBranches(tenantId);
    }

    @PreAuthorize("hasPermission(null, 'payments.view') or hasPermission(null, 'fees.view')")
    @Transactional(readOnly = true)
    public List<AddonRow> listAddons() {
        return catalog.listAddons(TenantContext.requireTenantId());
    }

    @PreAuthorize("hasPermission(null, 'settings.manage')")
    @Transactional
    public List<AddonRow> createAddon(String name, String description, long amountMinor, String currency) {
        UUID tenantId = TenantContext.requireTenantId();
        if (amountMinor < 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Amount cannot be negative.");
        }
        String cur = currency == null || currency.isBlank() ? "INR" : currency.toUpperCase(Locale.ROOT);
        catalog.insertAddon(tenantId, requireName(name), blankToNull(description), amountMinor, cur);
        return catalog.listAddons(tenantId);
    }

    @PreAuthorize("hasPermission(null, 'settings.manage')")
    @Transactional
    public List<AddonRow> patchAddon(UUID id, String name, String description, Long amountMinor, Boolean active) {
        UUID tenantId = TenantContext.requireTenantId();
        if (catalog.updateAddon(tenantId, id, blankToNull(name), blankToNull(description), amountMinor, active) == 0) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Add-on not found.");
        }
        return catalog.listAddons(tenantId);
    }

    @PreAuthorize("hasPermission(null, 'settings.manage')")
    @Transactional
    public List<AddonRow> deleteAddon(UUID id) {
        UUID tenantId = TenantContext.requireTenantId();
        if (catalog.softDeleteAddon(tenantId, id) == 0) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Add-on not found.");
        }
        return catalog.listAddons(tenantId);
    }

    @PreAuthorize("hasPermission(null, 'payments.record')")
    @Transactional
    public List<SaleRow> collectAddon(UUID productId, UUID customerId, Long amountMinor, String method, String notes) {
        UUID tenantId = TenantContext.requireTenantId();
        AddonRow product = catalog.findAddon(tenantId, productId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Add-on not found."));
        if (!product.active()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "This add-on is inactive.");
        }
        catalog.findMember(tenantId, customerId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Member not found."));
        long amount = amountMinor == null ? product.amountMinor() : amountMinor;
        String pay = method == null || method.isBlank() ? "CASH" : method.trim().toUpperCase(Locale.ROOT);
        if (!List.of("CASH", "UPI", "CARD", "OTHER").contains(pay)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown payment method.");
        }
        catalog.insertSale(
                tenantId, productId, customerId, amount, product.currency(), pay, blankToNull(notes),
                LocalDate.now(), CurrentUser.id());
        return catalog.listSales(tenantId);
    }

    @PreAuthorize("hasPermission(null, 'payments.view') or hasPermission(null, 'fees.view')")
    @Transactional(readOnly = true)
    public List<SaleRow> listSales() {
        return catalog.listSales(TenantContext.requireTenantId());
    }

    @PreAuthorize("hasPermission(null, 'customers.view')")
    @Transactional(readOnly = true)
    public List<DietRow> listDiets() {
        return catalog.listDiets(TenantContext.requireTenantId());
    }

    @PreAuthorize("hasPermission(null, 'settings.manage')")
    @Transactional
    public List<DietRow> createDiet(String name, String body) {
        UUID tenantId = TenantContext.requireTenantId();
        catalog.insertDiet(tenantId, requireName(name), requireBody(body));
        return catalog.listDiets(tenantId);
    }

    @PreAuthorize("hasPermission(null, 'settings.manage')")
    @Transactional
    public List<DietRow> patchDiet(UUID id, String name, String body) {
        UUID tenantId = TenantContext.requireTenantId();
        if (catalog.updateDiet(tenantId, id, blankToNull(name), blankToNull(body)) == 0) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Diet chart not found.");
        }
        return catalog.listDiets(tenantId);
    }

    @PreAuthorize("hasPermission(null, 'settings.manage')")
    @Transactional
    public List<DietRow> deleteDiet(UUID id) {
        UUID tenantId = TenantContext.requireTenantId();
        if (catalog.softDeleteDiet(tenantId, id) == 0) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Diet chart not found.");
        }
        return catalog.listDiets(tenantId);
    }

    @PreAuthorize("hasPermission(null, 'reminders.send')")
    @Transactional
    public List<DietSendView> sendDiet(UUID templateId, List<UUID> customerIds, UUID branchId) {
        UUID tenantId = TenantContext.requireTenantId();
        DietRow template = catalog.findDiet(tenantId, templateId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Diet chart not found."));
        java.util.LinkedHashMap<UUID, MemberContact> targets = new java.util.LinkedHashMap<>();
        if (branchId != null) {
            for (MemberContact m : catalog.listMembers(tenantId, branchId)) {
                targets.put(m.id(), m);
            }
        }
        if (customerIds != null) {
            for (UUID id : customerIds) {
                if (id == null) {
                    continue;
                }
                catalog.findMember(tenantId, id).ifPresent(m -> targets.put(m.id(), m));
            }
        }
        if (targets.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Choose members or a branch.");
        }
        var contact = tenants.findContact(tenantId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Business profile not found."));
        String fromLine = "";
        if (PhoneNumbers.hasNumber(contact.whatsappNumber()) || PhoneNumbers.hasNumber(contact.phone())) {
            String shown = PhoneNumbers.hasNumber(contact.whatsappNumber())
                    ? contact.whatsappNumber()
                    : contact.phone();
            fromLine = " — " + contact.name() + " (" + shown + ")";
        }
        String chart = com.feesaas.shared.text.HtmlPlain.text(template.body());
        List<DietSendView> out = new java.util.ArrayList<>();
        for (MemberContact member : targets.values()) {
            if (!PhoneNumbers.hasNumber(member.phone())) {
                continue;
            }
            String body = "Hi " + member.fullName() + ", here is your " + template.name() + " diet chart:"
                    + "\n\n" + chart
                    + fromLine;
            String channel = member.hasWhatsapp() ? "WHATSAPP" : "SMS";
            catalog.insertDietSend(tenantId, templateId, member.id(), channel, body, CurrentUser.id());
            out.add(new DietSendView(
                    channel,
                    body,
                    PhoneNumbers.waLink(member.phone(), body),
                    PhoneNumbers.smsLink(member.phone(), body),
                    contact.whatsappNumber(),
                    contact.phone(),
                    member.fullName()));
        }
        if (out.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "None of the selected members have a phone number.");
        }
        return out;
    }

    @PreAuthorize("hasPermission(null, 'platform.tenants.manage')")
    public List<BranchRow> platformBranches(UUID tenantId) {
        return executor.call(TenantScope.platformAdmin(), () -> catalog.listBranches(tenantId));
    }

    @PreAuthorize("hasPermission(null, 'platform.tenants.manage')")
    public List<BranchRow> platformCreateBranch(UUID tenantId, String name, String address, String phone, boolean primary) {
        return executor.call(TenantScope.platformAdmin(), () -> {
            catalog.insertBranch(tenantId, requireName(name), blankToNull(address), blankToNull(phone), primary);
            return catalog.listBranches(tenantId);
        });
    }

    @PreAuthorize("hasPermission(null, 'platform.tenants.manage')")
    public List<BranchRow> platformPatchBranch(UUID tenantId, UUID id, String name, String address, String phone, Boolean primary) {
        return executor.call(TenantScope.platformAdmin(), () -> {
            if (catalog.updateBranch(tenantId, id, blankToNull(name), blankToNull(address), blankToNull(phone), primary) == 0) {
                throw new ApiException(ErrorCode.NOT_FOUND, "Branch not found.");
            }
            return catalog.listBranches(tenantId);
        });
    }

    @PreAuthorize("hasPermission(null, 'platform.tenants.manage')")
    public List<BranchRow> platformDeleteBranch(UUID tenantId, UUID id) {
        return executor.call(TenantScope.platformAdmin(), () -> {
            if (catalog.softDeleteBranch(tenantId, id) == 0) {
                throw new ApiException(ErrorCode.NOT_FOUND, "Branch not found.");
            }
            return catalog.listBranches(tenantId);
        });
    }

    @PreAuthorize("hasPermission(null, 'platform.tenants.manage')")
    public List<AddonRow> platformAddons(UUID tenantId) {
        return executor.call(TenantScope.platformAdmin(), () -> catalog.listAddons(tenantId));
    }

    @PreAuthorize("hasPermission(null, 'platform.tenants.manage')")
    public List<AddonRow> platformCreateAddon(UUID tenantId, String name, String description, long amountMinor, String currency) {
        return executor.call(TenantScope.platformAdmin(), () -> {
            String cur = currency == null || currency.isBlank() ? "INR" : currency.toUpperCase(Locale.ROOT);
            catalog.insertAddon(tenantId, requireName(name), blankToNull(description), amountMinor, cur);
            return catalog.listAddons(tenantId);
        });
    }

    @PreAuthorize("hasPermission(null, 'platform.tenants.manage')")
    public List<AddonRow> platformPatchAddon(UUID tenantId, UUID id, String name, String description, Long amountMinor, Boolean active) {
        return executor.call(TenantScope.platformAdmin(), () -> {
            if (catalog.updateAddon(tenantId, id, blankToNull(name), blankToNull(description), amountMinor, active) == 0) {
                throw new ApiException(ErrorCode.NOT_FOUND, "Add-on not found.");
            }
            return catalog.listAddons(tenantId);
        });
    }

    @PreAuthorize("hasPermission(null, 'platform.tenants.manage')")
    public List<AddonRow> platformDeleteAddon(UUID tenantId, UUID id) {
        return executor.call(TenantScope.platformAdmin(), () -> {
            if (catalog.softDeleteAddon(tenantId, id) == 0) {
                throw new ApiException(ErrorCode.NOT_FOUND, "Add-on not found.");
            }
            return catalog.listAddons(tenantId);
        });
    }

    @PreAuthorize("hasPermission(null, 'platform.tenants.manage')")
    public List<DietRow> platformDiets(UUID tenantId) {
        return executor.call(TenantScope.platformAdmin(), () -> catalog.listDiets(tenantId));
    }

    @PreAuthorize("hasPermission(null, 'platform.tenants.manage')")
    public List<DietRow> platformCreateDiet(UUID tenantId, String name, String body) {
        return executor.call(TenantScope.platformAdmin(), () -> {
            catalog.insertDiet(tenantId, requireName(name), requireBody(body));
            return catalog.listDiets(tenantId);
        });
    }

    @PreAuthorize("hasPermission(null, 'platform.tenants.manage')")
    public List<DietRow> platformPatchDiet(UUID tenantId, UUID id, String name, String body) {
        return executor.call(TenantScope.platformAdmin(), () -> {
            if (catalog.updateDiet(tenantId, id, blankToNull(name), blankToNull(body)) == 0) {
                throw new ApiException(ErrorCode.NOT_FOUND, "Diet chart not found.");
            }
            return catalog.listDiets(tenantId);
        });
    }

    @PreAuthorize("hasPermission(null, 'platform.tenants.manage')")
    public List<DietRow> platformDeleteDiet(UUID tenantId, UUID id) {
        return executor.call(TenantScope.platformAdmin(), () -> {
            if (catalog.softDeleteDiet(tenantId, id) == 0) {
                throw new ApiException(ErrorCode.NOT_FOUND, "Diet chart not found.");
            }
            return catalog.listDiets(tenantId);
        });
    }

    private static String requireName(String name) {
        String trimmed = blankToNull(name);
        if (trimmed == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Name is required.");
        }
        return trimmed;
    }

    private static String requireBody(String body) {
        String trimmed = blankToNull(body);
        if (trimmed == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Diet chart text is required.");
        }
        return trimmed;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record DietSendView(
            String channel, String body, String waLink, String smsLink, String tenantWhatsapp, String tenantPhone, String memberName) {}
}
