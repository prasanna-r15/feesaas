package com.feesaas.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.feesaas.identity.application.SupportChatService;
import com.feesaas.identity.infra.SupportChatRepository;
import com.feesaas.identity.infra.SupportChatRepository.ThreadRow;
import com.feesaas.shared.tenancy.TenantExecutor;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class SupportChatServiceTest {

    @Mock
    SupportChatRepository chats;

    @Mock
    TenantExecutor tenants;

    @InjectMocks
    SupportChatService service;

    @BeforeEach
    void auth() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(UUID.randomUUID().toString())
                .claim("role", "PLATFORM_SUPER_ADMIN")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listThreadsKeepsUnreadCount() {
        UUID threadId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID userId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        when(tenants.call(any(), any())).thenAnswer(inv -> {
            Supplier<?> work = inv.getArgument(1);
            return work.get();
        });
        when(chats.listThreads()).thenReturn(List.of(new ThreadRow(
                threadId, userId, "Asha", "asha@example.com", "Need help", Instant.parse("2026-10-01T10:00:00Z"), 3)));
        var rows = service.listThreads();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).unreadCount()).isEqualTo(3);
    }
}
