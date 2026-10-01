package com.feesaas.attendance.application;

import com.feesaas.attendance.infra.BatchRepository;
import com.feesaas.customer.infra.CustomerRepository;
import com.feesaas.shared.error.ApiException;
import com.feesaas.shared.error.ErrorCode;
import com.feesaas.shared.tenancy.TenantContext;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AttendanceService {

    private final BatchRepository batches;
    private final CustomerRepository customers;

    public AttendanceService(BatchRepository batches, CustomerRepository customers) {
        this.batches = batches;
        this.customers = customers;
    }

    @PreAuthorize("hasPermission(null, 'batches.manage')")
    @Transactional(readOnly = true)
    public List<BatchView> listBatches() {
        TenantContext.requireTenantId();
        return batches.list().stream()
                .map(r -> new BatchView(r.id(), r.name(), r.schedule(), r.memberCount()))
                .toList();
    }

    @PreAuthorize("hasPermission(null, 'batches.manage')")
    @Transactional
    public BatchView createBatch(String name, String schedule) {
        UUID tenantId = TenantContext.requireTenantId();
        String n = requireName(name);
        if (batches.nameTaken(n, null)) {
            throw new ApiException(ErrorCode.CONFLICT, "A batch with this name already exists.");
        }
        UUID id = batches.insert(tenantId, n, blankToNull(schedule));
        return getBatch(id);
    }

    @PreAuthorize("hasPermission(null, 'batches.manage')")
    @Transactional
    public BatchView patchBatch(UUID id, String name, String schedule) {
        TenantContext.requireTenantId();
        requireBatch(id);
        if (name != null) {
            String n = requireName(name);
            if (batches.nameTaken(n, id)) {
                throw new ApiException(ErrorCode.CONFLICT, "A batch with this name already exists.");
            }
            batches.update(id, n, schedule);
        } else if (schedule != null) {
            batches.update(id, null, schedule);
        }
        return getBatch(id);
    }

    @PreAuthorize("hasPermission(null, 'batches.manage')")
    @Transactional
    public void deleteBatch(UUID id) {
        TenantContext.requireTenantId();
        if (batches.softDelete(id) == 0) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Batch not found.");
        }
    }

    @PreAuthorize("hasPermission(null, 'batches.manage') or hasPermission(null, 'attendance.manage')")
    @Transactional(readOnly = true)
    public List<MemberView> members(UUID batchId) {
        TenantContext.requireTenantId();
        requireBatch(batchId);
        return batches.members(batchId).stream()
                .map(m -> new MemberView(m.id(), m.customerCode(), m.fullName(), m.phone(), null))
                .toList();
    }

    @PreAuthorize("hasPermission(null, 'batches.manage') or hasPermission(null, 'attendance.manage')")
    @Transactional
    public void addMember(UUID batchId, UUID customerId) {
        UUID tenantId = TenantContext.requireTenantId();
        requireBatch(batchId);
        customers.findById(customerId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Member not found."));
        batches.addMember(tenantId, batchId, customerId);
    }

    @PreAuthorize("hasPermission(null, 'batches.manage')")
    @Transactional
    public void removeMember(UUID batchId, UUID customerId) {
        TenantContext.requireTenantId();
        requireBatch(batchId);
        batches.removeMember(batchId, customerId);
    }

    @PreAuthorize("hasPermission(null, 'attendance.manage')")
    @Transactional(readOnly = true)
    public RosterView roster(UUID batchId, LocalDate on) {
        TenantContext.requireTenantId();
        requireBatch(batchId);
        LocalDate day = on == null ? LocalDate.now() : on;
        Map<UUID, String> marks = batches.marks(batchId, day).stream()
                .collect(Collectors.toMap(BatchRepository.MarkRow::customerId, BatchRepository.MarkRow::status));
        List<MemberView> members = batches.members(batchId).stream()
                .map(m -> new MemberView(m.id(), m.customerCode(), m.fullName(), m.phone(), marks.get(m.id())))
                .toList();
        return new RosterView(batchId, day, members);
    }

    @PreAuthorize("hasPermission(null, 'attendance.manage')")
    @Transactional
    public RosterView mark(UUID batchId, LocalDate on, UUID customerId, String status) {
        UUID tenantId = TenantContext.requireTenantId();
        requireBatch(batchId);
        String st = status == null ? "" : status.trim().toUpperCase();
        if (!st.equals("PRESENT") && !st.equals("ABSENT")) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Mark PRESENT or ABSENT.");
        }
        LocalDate day = on == null ? LocalDate.now() : on;
        customers.findById(customerId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Member not found."));
        batches.upsertAttendance(tenantId, batchId, customerId, day, st);
        return roster(batchId, day);
    }

    private BatchView getBatch(UUID id) {
        var row = requireBatch(id);
        return new BatchView(row.id(), row.name(), row.schedule(), row.memberCount());
    }

    private BatchRepository.BatchRow requireBatch(UUID id) {
        return batches.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Batch not found."));
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Batch name is required.");
        }
        return name.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record BatchView(UUID id, String name, String schedule, long memberCount) {}

    public record MemberView(UUID id, String customerCode, String fullName, String phone, String status) {}

    public record RosterView(UUID batchId, LocalDate markedOn, List<MemberView> members) {}
}
