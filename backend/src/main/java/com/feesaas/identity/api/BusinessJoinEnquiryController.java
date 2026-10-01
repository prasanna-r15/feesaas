package com.feesaas.identity.api;

import com.feesaas.identity.application.BusinessJoinEnquiryService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BusinessJoinEnquiryController {

    private final BusinessJoinEnquiryService enquiries;

    public BusinessJoinEnquiryController(BusinessJoinEnquiryService enquiries) {
        this.enquiries = enquiries;
    }

    @GetMapping("/api/v1/me/business-enquiries")
    public List<AdminEnquiryResponse> mine() {
        return enquiries.mine().stream()
                .map(r -> new AdminEnquiryResponse(
                        r.id(), r.userId(), r.fullName(), r.email(), r.phone(),
                        r.businessName(), r.city(), r.message(), r.status(), r.createdAt()))
                .toList();
    }

    @PostMapping("/api/v1/me/business-enquiries")
    @ResponseStatus(HttpStatus.CREATED)
    public EnquiryResponse submit(@RequestBody SubmitRequest request) {
        var view = enquiries.submit(
                request == null ? null : request.businessName(),
                request == null ? null : request.city(),
                request == null ? null : request.message());
        return new EnquiryResponse(view.id(), view.emailed());
    }

    @GetMapping("/api/v1/platform/join-enquiries")
    public List<AdminEnquiryResponse> list() {
        return enquiries.list().stream()
                .map(r -> new AdminEnquiryResponse(
                        r.id(), r.userId(), r.fullName(), r.email(), r.phone(),
                        r.businessName(), r.city(), r.message(), r.status(), r.createdAt()))
                .toList();
    }

    @PostMapping("/api/v1/platform/join-enquiries/{id}/approve")
    public ApproveResponse approve(@PathVariable UUID id, @RequestBody ApproveRequest request) {
        var created = enquiries.approveAsNewBusiness(
                id,
                request == null ? null : request.name(),
                request == null ? null : request.slug(),
                request == null ? null : request.businessType(),
                request == null ? null : request.timezone(),
                request == null ? null : request.currency());
        return new ApproveResponse(created.id(), created.name(), created.slug());
    }

    public record SubmitRequest(String businessName, String city, String message) {}

    public record EnquiryResponse(UUID id, boolean emailed) {}

    public record ApproveRequest(String name, String slug, String businessType, String timezone, String currency) {}

    public record ApproveResponse(UUID tenantId, String name, String slug) {}

    public record AdminEnquiryResponse(
            UUID id,
            UUID userId,
            String fullName,
            String email,
            String phone,
            String businessName,
            String city,
            String message,
            String status,
            Instant createdAt
    ) {}
}
