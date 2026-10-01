package com.feesaas.identity.application;

import com.feesaas.identity.infra.SupportChatRepository;
import com.feesaas.identity.infra.SupportChatRepository.MessageRow;
import com.feesaas.identity.infra.SupportChatRepository.ThreadRow;
import com.feesaas.shared.error.ApiException;
import com.feesaas.shared.error.ErrorCode;
import com.feesaas.shared.security.CurrentUser;
import com.feesaas.shared.tenancy.TenantExecutor;
import com.feesaas.shared.tenancy.TenantScope;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupportChatService {

    private final SupportChatRepository chats;
    private final TenantExecutor tenants;

    public SupportChatService(SupportChatRepository chats, TenantExecutor tenants) {
        this.chats = chats;
        this.tenants = tenants;
    }

    @Transactional
    public MessageView postMine(String body) {
        UUID userId = CurrentUser.id();
        UUID threadId = chats.ensureThread(userId);
        MessageView view = insert(threadId, userId, false, body);
        chats.markUserRead(userId);
        return view;
    }

    @Transactional
    public List<MessageView> myMessages() {
        UUID userId = CurrentUser.id();
        UUID threadId = chats.ensureThread(userId);
        List<MessageView> rows = chats.messages(threadId).stream().map(SupportChatService::toView).toList();
        chats.markUserRead(userId);
        return rows;
    }

    @Transactional(readOnly = true)
    public long myUnread() {
        return chats.unreadForUser(CurrentUser.id());
    }

    @Transactional
    public void noteFromUser(UUID userId, String body) {
        if (userId == null || body == null || body.isBlank()) {
            return;
        }
        UUID threadId = chats.ensureThread(userId);
        chats.insertMessage(threadId, userId, false, body.trim());
    }

    @PreAuthorize("hasPermission(null, 'platform.tenants.manage')")
    public List<ThreadView> listThreads() {
        return asPlatform(() -> chats.listThreads().stream()
                .map(r -> new ThreadView(
                        r.id(), r.userId(), r.fullName(), r.email(), r.lastBody(), r.lastAt(), r.unreadCount()))
                .toList());
    }

    @PreAuthorize("hasPermission(null, 'platform.tenants.manage')")
    public long adminUnread() {
        return asPlatform(chats::unreadForPlatform);
    }

    @PreAuthorize("hasPermission(null, 'platform.tenants.manage')")
    public ThreadView openForUser(UUID userId) {
        if (userId == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "userId is required.");
        }
        return asPlatform(() -> {
            UUID threadId = chats.ensureThread(userId);
            return chats.listThreads().stream()
                    .filter(t -> t.id().equals(threadId))
                    .findFirst()
                    .map(r -> new ThreadView(
                            r.id(), r.userId(), r.fullName(), r.email(), r.lastBody(), r.lastAt(), r.unreadCount()))
                    .orElse(new ThreadView(threadId, userId, "", "", null, Instant.now(), 0));
        });
    }

    @PreAuthorize("hasPermission(null, 'platform.tenants.manage')")
    public List<MessageView> adminMessages(UUID threadId) {
        return asPlatform(() -> {
            requireThread(threadId);
            List<MessageView> rows = chats.messages(threadId).stream().map(SupportChatService::toView).toList();
            chats.markPlatformRead(threadId);
            return rows;
        });
    }

    @PreAuthorize("hasPermission(null, 'platform.tenants.manage')")
    public MessageView adminReply(UUID threadId, String body) {
        return asPlatform(() -> {
            requireThread(threadId);
            MessageView view = insert(threadId, CurrentUser.id(), true, body);
            chats.markPlatformRead(threadId);
            return view;
        });
    }

    private <T> T asPlatform(Supplier<T> work) {
        return tenants.call(TenantScope.platformAdmin().withUser(CurrentUser.id()), work);
    }

    private MessageView insert(UUID threadId, UUID authorId, boolean fromPlatform, String body) {
        String text = body == null ? "" : body.trim();
        if (text.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Write a message.");
        }
        UUID id = chats.insertMessage(threadId, authorId, fromPlatform, text);
        return chats.message(id).map(SupportChatService::toView)
                .orElse(new MessageView(id, threadId, authorId, fromPlatform, text, Instant.now(), null));
    }

    private void requireThread(UUID threadId) {
        chats.threadOwner(threadId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Chat not found."));
    }

    private static MessageView toView(MessageRow r) {
        return new MessageView(r.id(), r.threadId(), r.authorUserId(), r.fromPlatform(), r.body(), r.createdAt(), r.authorName());
    }

    public record MessageView(
            UUID id,
            UUID threadId,
            UUID authorUserId,
            boolean fromPlatform,
            String body,
            Instant createdAt,
            String authorName
    ) {}

    public record ThreadView(
            UUID id,
            UUID userId,
            String fullName,
            String email,
            String lastBody,
            Instant lastAt,
            long unreadCount
    ) {}
}
