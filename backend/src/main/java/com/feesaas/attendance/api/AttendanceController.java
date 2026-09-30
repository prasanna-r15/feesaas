package com.feesaas.attendance.api;

import com.feesaas.attendance.application.AttendanceService;
import com.feesaas.attendance.application.AttendanceService.BatchView;
import com.feesaas.attendance.application.AttendanceService.MemberView;
import com.feesaas.attendance.application.AttendanceService.RosterView;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AttendanceController {

    private final AttendanceService attendance;

    public AttendanceController(AttendanceService attendance) {
        this.attendance = attendance;
    }

    @GetMapping("/api/v1/batches")
    public List<BatchResponse> listBatches() {
        return attendance.listBatches().stream().map(AttendanceController::toBatch).toList();
    }

    @PostMapping("/api/v1/batches")
    @ResponseStatus(HttpStatus.CREATED)
    public BatchResponse createBatch(@RequestBody BatchBody body) {
        return toBatch(attendance.createBatch(body == null ? null : body.name(), body == null ? null : body.schedule()));
    }

    @PatchMapping("/api/v1/batches/{id}")
    public BatchResponse patchBatch(@PathVariable UUID id, @RequestBody BatchBody body) {
        return toBatch(attendance.patchBatch(id, body.name(), body.schedule()));
    }

    @DeleteMapping("/api/v1/batches/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBatch(@PathVariable UUID id) {
        attendance.deleteBatch(id);
    }

    @GetMapping("/api/v1/batches/{id}/members")
    public List<MemberResponse> members(@PathVariable UUID id) {
        return attendance.members(id).stream().map(AttendanceController::toMember).toList();
    }

    @PostMapping("/api/v1/batches/{id}/members")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addMember(@PathVariable UUID id, @RequestBody MemberBody body) {
        attendance.addMember(id, body.customerId());
    }

    @DeleteMapping("/api/v1/batches/{id}/members/{customerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@PathVariable UUID id, @PathVariable UUID customerId) {
        attendance.removeMember(id, customerId);
    }

    @GetMapping("/api/v1/attendance")
    public RosterResponse roster(@RequestParam UUID batchId, @RequestParam(required = false) LocalDate on) {
        return toRoster(attendance.roster(batchId, on));
    }

    @PostMapping("/api/v1/attendance")
    public RosterResponse mark(@RequestBody MarkBody body) {
        return toRoster(attendance.mark(body.batchId(), body.markedOn(), body.customerId(), body.status()));
    }

    private static BatchResponse toBatch(BatchView view) {
        return new BatchResponse(view.id(), view.name(), view.schedule(), view.memberCount());
    }

    private static MemberResponse toMember(MemberView view) {
        return new MemberResponse(view.id(), view.customerCode(), view.fullName(), view.phone(), view.status());
    }

    private static RosterResponse toRoster(RosterView view) {
        return new RosterResponse(
                view.batchId(),
                view.markedOn(),
                view.members().stream().map(AttendanceController::toMember).toList());
    }

    public record BatchBody(String name, String schedule) {}

    public record MemberBody(UUID customerId) {}

    public record MarkBody(UUID batchId, UUID customerId, LocalDate markedOn, String status) {}

    public record BatchResponse(UUID id, String name, String schedule, long memberCount) {}

    public record MemberResponse(UUID id, String customerCode, String fullName, String phone, String status) {}

    public record RosterResponse(UUID batchId, LocalDate markedOn, List<MemberResponse> members) {}
}
