package com.feesaas.customer.application;

import com.feesaas.customer.infra.CustomerRepository;
import com.feesaas.customer.infra.CustomerRepository.CustomerRow;
import com.feesaas.fee.application.FeeEnrollmentService;
import com.feesaas.shared.error.ApiException;
import com.feesaas.shared.error.ErrorCode;
import com.feesaas.shared.security.CurrentUser;
import com.feesaas.shared.tenancy.TenantContext;
import com.feesaas.tenant.application.UsageGuard;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

    private final CustomerRepository customers;
    private final FeeEnrollmentService enrollment;
    private final UsageGuard usage;

    public CustomerService(CustomerRepository customers, FeeEnrollmentService enrollment, UsageGuard usage) {
        this.customers = customers;
        this.enrollment = enrollment;
        this.usage = usage;
    }

    @PreAuthorize("hasPermission(null, 'customers.view')")
    @Transactional(readOnly = true)
    public List<CustomerView> list(String query, String status) {
        return list(query, status, null);
    }

    @PreAuthorize("hasPermission(null, 'customers.view')")
    @Transactional(readOnly = true)
    public List<CustomerView> list(String query, String status, UUID branchId) {
        TenantContext.requireTenantId();
        return customers.list(query, blankToNull(status), branchId).stream().map(this::toView).toList();
    }

    @PreAuthorize("hasPermission(null, 'customers.view')")
    @Transactional(readOnly = true)
    public byte[] exportXlsx() {
        TenantContext.requireTenantId();
        List<String> headers = List.of(
                "Full name", "Phone", "Email", "Due date (YYYY-MM-DD)", "Fee plan", "Has WhatsApp", "Notes", "Branch");
        List<List<String>> rows = customers.list("", null, null).stream()
                .map(c -> List.of(
                        n(c.fullName()),
                        n(c.phone()),
                        n(c.email()),
                        c.dueDate() == null ? "" : c.dueDate().toString(),
                        n(c.feePlanName()),
                        c.hasWhatsapp() ? "TRUE" : "FALSE",
                        n(c.notes()),
                        n(c.branchName())))
                .toList();
        return com.feesaas.shared.export.Spreadsheets.xlsx(
                "Members", headers, rows, List.of(24.0, 20.0, 32.0, 18.0, 16.0, 16.0, 28.0, 20.0), java.util.Set.of(3));
    }

    private static String n(String value) {
        return value == null ? "" : value;
    }

    @PreAuthorize("hasPermission(null, 'customers.view')")
    @Transactional(readOnly = true)
    public CustomerView get(UUID id) {
        TenantContext.requireTenantId();
        return toView(require(id));
    }

    @PreAuthorize("hasPermission(null, 'customers.create')")
    @Transactional
    public CustomerView create(CreateCustomerCommand cmd) {
        UUID tenantId = TenantContext.requireTenantId();
        usage.assertCanAddMembers(1);
        String phone = blankToNull(cmd.phone());
        String email = blankToNull(cmd.email());
        if (phone == null && email == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Phone or email is required.");
        }
        if (cmd.dueDate() == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Due date is required.");
        }
        assertUnique(phone, email, null);
        String code = "C" + String.format("%04d", customers.nextCodeNumber());
        UUID id = customers.insert(
                tenantId,
                code,
                cmd.fullName().trim(),
                phone,
                email,
                blankToNull(cmd.notes()),
                cmd.dueDate(),
                cmd.hasWhatsapp() == null || cmd.hasWhatsapp(),
                CurrentUser.id(),
                cmd.branchId());
        enrollment.enrollAndGenerate(id, cmd.dueDate(), cmd.feePlanId());
        return toView(require(id));
    }

    @PreAuthorize("hasPermission(null, 'customers.edit')")
    @Transactional
    public CustomerView patch(UUID id, PatchCustomerCommand cmd) {
        TenantContext.requireTenantId();
        CustomerRow current = require(id);
        String phone = cmd.phone() == null ? current.phone() : blankToNull(cmd.phone());
        String email = cmd.email() == null ? current.email() : blankToNull(cmd.email());
        if (cmd.phone() != null || cmd.email() != null) {
            if (phone == null && email == null) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Phone or email is required.");
            }
            assertUnique(phone, email, id);
        }
        if (customers.update(
                id,
                blankToNull(cmd.fullName()),
                cmd.phone() == null ? null : phone,
                cmd.email() == null ? null : email,
                cmd.status(),
                cmd.notes(),
                cmd.dueDate(),
                cmd.hasWhatsapp(),
                cmd.branchId()) == 0) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Customer not found.");
        }
        if (cmd.feePlanId() != null) {
            LocalDate due = cmd.dueDate() != null ? cmd.dueDate() : current.dueDate();
            enrollment.enrollAndGenerate(id, due, cmd.feePlanId());
        } else if (cmd.dueDate() != null) {
            enrollment.syncDueDate(id, cmd.dueDate());
        }
        return toView(require(id));
    }

    @PreAuthorize("hasPermission(null, 'customers.delete')")
    @Transactional
    public void delete(UUID id) {
        TenantContext.requireTenantId();
        if (customers.softDelete(id) == 0) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Customer not found.");
        }
    }

    private void assertUnique(String phone, String email, UUID excluding) {
        if (customers.phoneTaken(phone, excluding)) {
            throw new ApiException(ErrorCode.CONFLICT, "A customer with this phone already exists.");
        }
        if (customers.emailTaken(email, excluding)) {
            throw new ApiException(ErrorCode.CONFLICT, "A customer with this email already exists.");
        }
    }

    private CustomerRow require(UUID id) {
        return customers.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Customer not found."));
    }

    private CustomerView toView(CustomerRow row) {
        return new CustomerView(
                row.id(),
                row.customerCode(),
                row.fullName(),
                row.phone(),
                row.email(),
                row.status(),
                row.notes(),
                row.dueDate(),
                row.createdAt(),
                row.hasWhatsapp(),
                row.feePlanId(),
                row.feePlanName(),
                row.branchId(),
                row.branchName());
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record CreateCustomerCommand(
            String fullName, String phone, String email, String notes, LocalDate dueDate, UUID feePlanId, Boolean hasWhatsapp, UUID branchId) {}

    public record PatchCustomerCommand(
            String fullName, String phone, String email, String status, String notes, LocalDate dueDate, UUID feePlanId, Boolean hasWhatsapp, UUID branchId) {}

    public record CustomerView(
            UUID id,
            String customerCode,
            String fullName,
            String phone,
            String email,
            String status,
            String notes,
            LocalDate dueDate,
            Instant createdAt,
            boolean hasWhatsapp,
            UUID feePlanId,
            String feePlanName,
            UUID branchId,
            String branchName
    ) {}
}
