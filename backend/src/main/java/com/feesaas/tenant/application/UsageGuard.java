package com.feesaas.tenant.application;

import com.feesaas.customer.infra.CustomerRepository;
import com.feesaas.shared.error.ApiException;
import com.feesaas.shared.error.ErrorCode;
import com.feesaas.shared.tenancy.TenantContext;
import com.feesaas.tenant.infra.TenantRepository;
import com.feesaas.user.infra.StaffRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class UsageGuard {

    private final TenantRepository tenants;
    private final CustomerRepository customers;
    private final StaffRepository staff;

    public UsageGuard(TenantRepository tenants, CustomerRepository customers, StaffRepository staff) {
        this.tenants = tenants;
        this.customers = customers;
        this.staff = staff;
    }

    public void assertCanAddMembers(int adding) {
        UUID tenantId = TenantContext.requireTenantId();
        var limits = tenants.findLimits(tenantId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Business profile not found."));
        long used = customers.countActive();
        if (used + adding > limits.maxMembers()) {
            throw new ApiException(
                    ErrorCode.PLAN_LIMIT_EXCEEDED,
                    "This " + limits.planCode() + " plan allows " + limits.maxMembers()
                            + " members. Upgrade the gym to add more.");
        }
    }

    public void assertCanAddStaff() {
        UUID tenantId = TenantContext.requireTenantId();
        var limits = tenants.findLimits(tenantId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Business profile not found."));
        if (staff.countStaff() >= limits.maxStaff()) {
            throw new ApiException(
                    ErrorCode.PLAN_LIMIT_EXCEEDED,
                    "This " + limits.planCode() + " plan allows " + limits.maxStaff()
                            + " staff seats. Upgrade the gym to add more.");
        }
    }
}
