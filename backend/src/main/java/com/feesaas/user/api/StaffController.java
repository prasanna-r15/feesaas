package com.feesaas.user.api;

import com.feesaas.user.api.dto.CreateStaffRequest;
import com.feesaas.user.api.dto.PatchStaffRequest;
import com.feesaas.user.api.dto.PermissionResponse;
import com.feesaas.user.api.dto.ReplacePermissionsRequest;
import com.feesaas.user.api.dto.StaffResponse;
import com.feesaas.user.application.StaffService;
import com.feesaas.user.application.StaffService.CreateStaffCommand;
import com.feesaas.user.application.StaffService.PatchStaffCommand;
import com.feesaas.user.application.StaffService.StaffView;
import jakarta.validation.Valid;
import java.util.List;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StaffController {

    private final StaffService staff;

    public StaffController(StaffService staff) {
        this.staff = staff;
    }

    @GetMapping("/api/v1/staff")
    public List<StaffResponse> list() {
        return staff.list().stream().map(StaffController::toResponse).toList();
    }

    @PostMapping("/api/v1/staff")
    @ResponseStatus(HttpStatus.CREATED)
    public StaffResponse create(@Valid @RequestBody CreateStaffRequest request) {
        return toResponse(staff.create(new CreateStaffCommand(
                request.fullName().trim(),
                request.email(),
                request.phone(),
                request.password(),
                request.permissions())));
    }

    @GetMapping("/api/v1/staff/{id}")
    public StaffResponse get(@PathVariable UUID id) {
        return toResponse(staff.get(id));
    }

    @PatchMapping("/api/v1/staff/{id}")
    public StaffResponse patch(@PathVariable UUID id, @Valid @RequestBody PatchStaffRequest request) {
        return toResponse(staff.patch(id, new PatchStaffCommand(
                request.fullName(), request.email(), request.phone(), request.status())));
    }

    @DeleteMapping("/api/v1/staff/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        staff.delete(id);
    }

    @PutMapping("/api/v1/staff/{id}/permissions")
    public StaffResponse permissions(@PathVariable UUID id, @Valid @RequestBody ReplacePermissionsRequest request) {
        return toResponse(staff.replacePermissions(id, request.permissions()));
    }

    @GetMapping("/api/v1/roles")
    public List<PermissionResponse> roles() {
        return staff.permissionCatalogue().stream()
                .map(p -> new PermissionResponse(p.code(), p.module(), p.description()))
                .toList();
    }

    private static StaffResponse toResponse(StaffView view) {
        return new StaffResponse(
                view.id(), view.fullName(), view.email(), view.phone(), view.role(),
                view.status(), view.createdAt(), view.permissions());
    }
}
