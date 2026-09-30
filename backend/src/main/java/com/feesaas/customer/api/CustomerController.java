package com.feesaas.customer.api;

import com.feesaas.customer.api.dto.CreateCustomerRequest;
import com.feesaas.customer.api.dto.CustomerResponse;
import com.feesaas.customer.api.dto.PatchCustomerRequest;
import com.feesaas.customer.application.CustomerService;
import com.feesaas.customer.application.CustomerService.CreateCustomerCommand;
import com.feesaas.customer.application.CustomerService.CustomerView;
import com.feesaas.customer.application.CustomerService.PatchCustomerCommand;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerService customers;

    public CustomerController(CustomerService customers) {
        this.customers = customers;
    }

    @GetMapping
    public List<CustomerResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID branchId) {
        return customers.list(q, status, branchId).stream().map(CustomerController::toResponse).toList();
    }

    @GetMapping("/export")
    public org.springframework.http.ResponseEntity<byte[]> export() {
        byte[] body = customers.exportXlsx();
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        org.springframework.http.ContentDisposition.attachment()
                                .filename("duemate-members.xlsx")
                                .build()
                                .toString())
                .contentType(org.springframework.http.MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse create(@Valid @RequestBody CreateCustomerRequest request) {
        return toResponse(customers.create(new CreateCustomerCommand(
                request.fullName(), request.phone(), request.email(), request.notes(), request.dueDate(), request.feePlanId(), request.hasWhatsapp(), request.branchId())));
    }

    @GetMapping("/{id}")
    public CustomerResponse get(@PathVariable UUID id) {
        return toResponse(customers.get(id));
    }

    @PatchMapping("/{id}")
    public CustomerResponse patch(@PathVariable UUID id, @Valid @RequestBody PatchCustomerRequest request) {
        return toResponse(customers.patch(id, new PatchCustomerCommand(
                request.fullName(), request.phone(), request.email(), request.status(), request.notes(), request.dueDate(), request.feePlanId(), request.hasWhatsapp(), request.branchId())));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        customers.delete(id);
    }

    private static CustomerResponse toResponse(CustomerView view) {
        return new CustomerResponse(
                view.id(), view.customerCode(), view.fullName(), view.phone(), view.email(),
                view.status(), view.notes(), view.dueDate(), view.createdAt(), view.hasWhatsapp(), view.feePlanId(), view.feePlanName(), view.branchId(), view.branchName());
    }
}
