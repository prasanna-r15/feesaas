package com.feesaas.customer.application;

import com.feesaas.catalog.infra.CatalogRepository;
import com.feesaas.customer.infra.CustomerRepository;
import com.feesaas.fee.application.FeeEnrollmentService;
import com.feesaas.fee.infra.FeePlanRepository;
import com.feesaas.shared.error.ApiException;
import com.feesaas.shared.error.ErrorCode;
import com.feesaas.shared.export.Spreadsheets;
import com.feesaas.shared.export.XlsxRows;
import com.feesaas.shared.security.CurrentUser;
import com.feesaas.shared.tenancy.TenantContext;
import com.feesaas.tenant.application.UsageGuard;
import com.feesaas.tenant.infra.TenantRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerImportService {

    public static final List<String> TEMPLATE_HEADERS = List.of(
            "Full name", "Phone", "Email", "Due date (YYYY-MM-DD)", "Fee plan", "Has WhatsApp", "Notes", "Branch");

    private final CustomerRepository customers;
    private final FeeEnrollmentService enrollment;
    private final FeePlanRepository plans;
    private final TenantRepository tenants;
    private final UsageGuard usage;
    private final CatalogRepository catalog;

    public CustomerImportService(
            CustomerRepository customers,
            FeeEnrollmentService enrollment,
            FeePlanRepository plans,
            TenantRepository tenants,
            UsageGuard usage,
            CatalogRepository catalog) {
        this.customers = customers;
        this.enrollment = enrollment;
        this.plans = plans;
        this.tenants = tenants;
        this.usage = usage;
        this.catalog = catalog;
    }

    @PreAuthorize("hasPermission(null, 'imports.run')")
    public byte[] template() {
        return Spreadsheets.xlsx(
                "Members",
                TEMPLATE_HEADERS,
                List.of(
                        List.of(
                                "Rahul Sharma", "+919876543210", "rahul@example.com",
                                "2026-10-15", "General", "TRUE", "Morning batch", "Karamadai"),
                        List.of(
                                "Anita Desai", "+919876543211", "",
                                "2026-10-20", "Cardio", "FALSE", "", "Teachers Colony")),
                List.of(24.0, 20.0, 32.0, 18.0, 16.0, 16.0, 28.0, 20.0),
                Set.of(3));
    }

    @PreAuthorize("hasPermission(null, 'imports.run')")
    @Transactional
    public ImportResult importFile(byte[] bytes) {
        UUID tenantId = TenantContext.requireTenantId();
        List<List<String>> table = XlsxRows.looksLikeZip(bytes) ? XlsxRows.read(bytes) : csvTable(bytes);
        if (table.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "The file is empty.");
        }
        int created = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        boolean header = true;
        int rowNum = 0;
        UUID defaultPlan = defaultPlanId(tenantId);
        for (List<String> cols : table) {
            rowNum++;
            if (cols.stream().allMatch(c -> c == null || c.isBlank())) {
                continue;
            }
            if (header) {
                header = false;
                if (looksLikeHeader(cols)) {
                    continue;
                }
            }
            try {
                if (importRow(tenantId, cols, defaultPlan)) {
                    created++;
                } else {
                    skipped++;
                }
            } catch (ApiException e) {
                if (e.code() == ErrorCode.PLAN_LIMIT_EXCEEDED) {
                    errors.add("Row " + rowNum + ": " + e.getMessage());
                    break;
                }
                errors.add("Row " + rowNum + ": " + e.getMessage());
            } catch (RuntimeException e) {
                errors.add("Row " + rowNum + ": " + e.getMessage());
            }
        }
        return new ImportResult(created, skipped, errors);
    }

    private static List<List<String>> csvTable(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8).replace("\uFEFF", "");
        List<List<String>> rows = new ArrayList<>();
        for (String line : text.lines().toList()) {
            if (!line.isBlank()) {
                rows.add(parseLine(line));
            }
        }
        return rows;
    }

    private boolean importRow(UUID tenantId, List<String> cols, UUID defaultPlan) {
        String name = col(cols, 0);
        String phone = blankToNull(XlsxRows.normalizePhone(col(cols, 1)));
        String email = blankToNull(col(cols, 2));
        String dueRaw = col(cols, 3);
        String planName = col(cols, 4);
        String hasWaRaw = col(cols, 5);
        String notes = blankToNull(col(cols, 6));
        String branchName = blankToNull(col(cols, 7));
        if (name.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "full_name is required.");
        }
        if (phone == null && email == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "phone or email is required.");
        }
        if (phone != null && customers.phoneTaken(phone, null)) {
            return false;
        }
        if (email != null && customers.emailTaken(email, null)) {
            return false;
        }
        LocalDate due;
        try {
            due = XlsxRows.parseDate(dueRaw);
            if (due == null) {
                throw new DateTimeParseException("blank", "", 0);
            }
        } catch (DateTimeParseException e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "due_date must be YYYY-MM-DD.");
        }
        usage.assertCanAddMembers(1);
        UUID planId = defaultPlan;
        if (!planName.isBlank()) {
            planId = plans.findByName(planName)
                    .map(FeePlanRepository.PlanRow::id)
                    .orElseThrow(() -> new ApiException(
                            ErrorCode.VALIDATION_FAILED, "Unknown fee plan '" + planName + "'."));
        }
        boolean hasWa = parseBool(hasWaRaw, true);
        UUID branchId = null;
        if (branchName != null) {
            branchId = catalog.findBranchByName(tenantId, branchName)
                    .orElseThrow(() -> new ApiException(
                            ErrorCode.VALIDATION_FAILED, "Unknown branch '" + branchName + "'. Add it under Locations first."))
                    .id();
        }
        String code = "C" + String.format("%04d", customers.nextCodeNumber());
        UUID id = customers.insert(
                tenantId, code, name.trim(), phone, email, notes, due, hasWa, CurrentUser.id(), branchId);
        enrollment.enrollAndGenerate(id, due, planId);
        return true;
    }

    private UUID defaultPlanId(UUID tenantId) {
        String currency = tenants.findById(tenantId).map(TenantRepository.TenantRow::currency).orElse("INR");
        return plans.ensureDefault(tenantId, currency);
    }

    private static boolean looksLikeHeader(List<String> cols) {
        String first = col(cols, 0).toLowerCase(Locale.ROOT);
        return first.contains("full_name") || first.contains("name");
    }

    private static boolean parseBool(String raw, boolean fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String v = raw.trim().toLowerCase(Locale.ROOT);
        if (List.of("true", "yes", "y", "1").contains(v)) {
            return true;
        }
        if (List.of("false", "no", "n", "0").contains(v)) {
            return false;
        }
        return fallback;
    }

    private static String col(List<String> cols, int i) {
        if (i >= cols.size() || cols.get(i) == null) {
            return "";
        }
        return cols.get(i).trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static List<String> parseLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }

    public record ImportResult(int created, int skipped, List<String> errors) {}
}
