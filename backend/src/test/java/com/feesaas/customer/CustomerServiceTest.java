package com.feesaas.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.feesaas.customer.application.CustomerService;
import com.feesaas.customer.application.CustomerService.CreateCustomerCommand;
import com.feesaas.customer.application.CustomerService.PatchCustomerCommand;
import com.feesaas.customer.infra.CustomerRepository;
import com.feesaas.customer.infra.CustomerRepository.CustomerRow;
import com.feesaas.tenant.application.UsageGuard;
import com.feesaas.fee.application.FeeEnrollmentService;
import com.feesaas.shared.error.ApiException;
import com.feesaas.shared.error.ErrorCode;
import com.feesaas.shared.tenancy.TenantContext;
import com.feesaas.shared.tenancy.TenantScope;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    CustomerRepository repository;

    @Mock
    FeeEnrollmentService enrollment;

    @Mock
    UsageGuard usage;

    CustomerService service;

    private final UUID tenantId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private final UUID userId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private final UUID customerId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @BeforeEach
    void setUp() {
        service = new CustomerService(repository, enrollment, usage);
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(userId.toString())
                .issuedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .expiresAt(Instant.parse("2026-01-01T01:00:00Z"))
                .claim("tid", tenantId.toString())
                .build();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
        SecurityContextHolder.setContext(context);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listIncludesDueDate() {
        LocalDate due = LocalDate.of(2026, 10, 15);
        when(repository.list(null, null)).thenReturn(List.of(row(due)));

        var views = TenantContext.callAs(TenantScope.tenant(tenantId), () -> service.list(null, null));

        assertThat(views).hasSize(1);
        assertThat(views.getFirst().dueDate()).isEqualTo(due);
        assertThat(views.getFirst().fullName()).isEqualTo("Rahul Sharma");
    }

    @Test
    void createRequiresDueDate() {
        TenantContext.callAs(TenantScope.tenant(tenantId), () -> {
            assertThatThrownBy(() -> service.create(
                            new CreateCustomerCommand("Rahul", "+91", null, null, null, null, null, null)))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).code())
                    .isEqualTo(ErrorCode.VALIDATION_FAILED);
            return null;
        });
    }

    @Test
    void createPersistsDueDate() {
        LocalDate due = LocalDate.of(2026, 10, 15);
        when(repository.nextCodeNumber()).thenReturn(1L);
        when(repository.insert(
                        eq(tenantId),
                        eq("C0001"),
                        eq("Rahul"),
                        eq("+91"),
                        isNull(),
                        isNull(),
                        eq(due),
                        eq(true),
                        eq(userId),
                        isNull()))
                .thenReturn(customerId);
        when(repository.findById(customerId)).thenReturn(Optional.of(row(due)));

        var view = TenantContext.callAs(
                TenantScope.tenant(tenantId),
                () -> service.create(new CreateCustomerCommand("Rahul", "+91", null, null, due, null, null, null)));

        assertThat(view.dueDate()).isEqualTo(due);
        assertThat(view.customerCode()).isEqualTo("C0001");
        verify(enrollment).enrollAndGenerate(customerId, due, null);
        verify(repository).insert(
                eq(tenantId),
                eq("C0001"),
                eq("Rahul"),
                eq("+91"),
                isNull(),
                isNull(),
                eq(due),
                eq(true),
                eq(userId),
                isNull());
    }

    @Test
    void patchUpdatesDueDate() {
        LocalDate next = LocalDate.of(2026, 11, 1);
        when(repository.findById(customerId))
                .thenReturn(Optional.of(row(LocalDate.of(2026, 10, 15))))
                .thenReturn(Optional.of(row(next)));
        when(repository.update(eq(customerId), isNull(), isNull(), isNull(), isNull(), isNull(), eq(next), isNull(), isNull()))
                .thenReturn(1);

        var view = TenantContext.callAs(
                TenantScope.tenant(tenantId),
                () -> service.patch(
                        customerId, new PatchCustomerCommand(null, null, null, null, null, next, null, null, null)));

        assertThat(view.dueDate()).isEqualTo(next);
        verify(repository).update(eq(customerId), isNull(), isNull(), isNull(), isNull(), isNull(), eq(next), isNull(), isNull());
    }

    @Test
    void deleteSoftDeletes() {
        when(repository.softDelete(customerId)).thenReturn(1);
        TenantContext.callAs(TenantScope.tenant(tenantId), () -> {
            service.delete(customerId);
            return null;
        });
        verify(repository).softDelete(customerId);
    }

    private CustomerRow row(LocalDate due) {
        return new CustomerRow(
                customerId,
                "C0001",
                "Rahul Sharma",
                "+91",
                null,
                "ACTIVE",
                null,
                due,
                Instant.parse("2026-01-01T00:00:00Z"),
                true,
                null,
                null,
                null,
                null);
    }
}
