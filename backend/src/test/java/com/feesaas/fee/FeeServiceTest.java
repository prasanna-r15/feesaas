package com.feesaas.fee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.feesaas.fee.application.FeeService;
import com.feesaas.fee.application.FeeService.CollectCommand;
import com.feesaas.fee.infra.FeeRepository;
import com.feesaas.fee.infra.FeeRepository.LockedFee;
import com.feesaas.fee.infra.FeeRepository.RemindRow;
import com.feesaas.fee.infra.PaymentRepository;
import com.feesaas.shared.error.ApiException;
import com.feesaas.shared.tenancy.TenantContext;
import com.feesaas.shared.tenancy.TenantScope;
import com.feesaas.tenant.infra.TenantRepository;
import com.feesaas.tenant.infra.TenantRepository.ContactRow;
import java.time.LocalDate;
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
class FeeServiceTest {

    @Mock
    FeeRepository fees;

    @Mock
    PaymentRepository payments;

    @Mock
    TenantRepository tenants;

    FeeService service;

    private final UUID tenantId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private final UUID userId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private final UUID feeId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private final UUID customerId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @BeforeEach
    void setUp() {
        service = new FeeService(fees, payments, tenants);
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(userId.toString())
                .issuedAt(java.time.Instant.parse("2026-01-01T00:00:00Z"))
                .expiresAt(java.time.Instant.parse("2026-01-01T01:00:00Z"))
                .build();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new JwtAuthenticationToken(jwt, java.util.List.of()));
        SecurityContextHolder.setContext(context);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void formatsInrFromMinorUnits() {
        assertThat(FeeService.formatMoney(150_000, "INR")).isEqualTo("₹1,500.00");
        assertThat(FeeService.formatMoney(0, "INR")).isEqualTo("₹0.00");
    }

    @Test
    void collectRejectsOverpayment() {
        when(fees.lockById(feeId)).thenReturn(Optional.of(new LockedFee(
                feeId, customerId, 150_000, 0, 0, "INR", "PENDING", 0)));
        TenantContext.callAs(TenantScope.tenant(tenantId), () -> {
            assertThatThrownBy(() -> service.collect(feeId, new CollectCommand(200_000L, "CASH", null, null)))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("outstanding");
            return null;
        });
    }

    @Test
    void remindUsesWhatsAppWhenMemberHasIt() {
        when(fees.findForRemind(feeId)).thenReturn(Optional.of(remindRow(true)));
        when(tenants.findContact(tenantId)).thenReturn(Optional.of(
                new ContactRow("Demo Gym", "+919999999999", "+919999999999")));
        TenantContext.callAs(TenantScope.tenant(tenantId), () -> {
            var view = service.remind(feeId);
            assertThat(view.channel()).isEqualTo("WHATSAPP");
            assertThat(view.waLink()).startsWith("https://wa.me/919876543210");
            assertThat(view.body()).contains("Demo Gym");
            return null;
        });
    }

    @Test
    void remindUsesSmsWhenMemberHasNoWhatsApp() {
        when(fees.findForRemind(feeId)).thenReturn(Optional.of(remindRow(false)));
        when(tenants.findContact(tenantId)).thenReturn(Optional.of(
                new ContactRow("Demo Gym", "+919999999999", "+919888888888")));
        TenantContext.callAs(TenantScope.tenant(tenantId), () -> {
            var view = service.remind(feeId);
            assertThat(view.channel()).isEqualTo("SMS");
            assertThat(view.body()).contains("Reach us at +919888888888");
            return null;
        });
    }

    private RemindRow remindRow(boolean hasWhatsapp) {
        return new RemindRow(
                feeId,
                "Rahul Sharma",
                "9876543210",
                hasWhatsapp,
                LocalDate.of(2026, 10, 15),
                150_000,
                "INR",
                "General");
    }
}
