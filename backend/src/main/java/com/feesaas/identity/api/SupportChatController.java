package com.feesaas.identity.api;

import com.feesaas.identity.application.SupportChatService;
import com.feesaas.identity.application.SupportChatService.MessageView;
import com.feesaas.identity.application.SupportChatService.ThreadView;
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
public class SupportChatController {

    private final SupportChatService chats;

    public SupportChatController(SupportChatService chats) {
        this.chats = chats;
    }

    @GetMapping("/api/v1/me/support/messages")
    public List<MessageResponse> mine() {
        return chats.myMessages().stream().map(SupportChatController::message).toList();
    }

    @GetMapping("/api/v1/me/support/unread")
    public UnreadResponse myUnread() {
        return new UnreadResponse(chats.myUnread());
    }

    @PostMapping("/api/v1/me/support/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse sendMine(@RequestBody BodyRequest request) {
        return message(chats.postMine(request == null ? null : request.body()));
    }

    @GetMapping("/api/v1/platform/support/threads")
    public List<ThreadResponse> threads() {
        return chats.listThreads().stream().map(SupportChatController::thread).toList();
    }

    @GetMapping("/api/v1/platform/support/unread")
    public UnreadResponse adminUnread() {
        return new UnreadResponse(chats.adminUnread());
    }

    @PostMapping("/api/v1/platform/support/threads")
    public ThreadResponse open(@RequestBody OpenRequest request) {
        return thread(chats.openForUser(request == null ? null : request.userId()));
    }

    @GetMapping("/api/v1/platform/support/threads/{id}/messages")
    public List<MessageResponse> adminMessages(@PathVariable UUID id) {
        return chats.adminMessages(id).stream().map(SupportChatController::message).toList();
    }

    @PostMapping("/api/v1/platform/support/threads/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse adminReply(@PathVariable UUID id, @RequestBody BodyRequest request) {
        return message(chats.adminReply(id, request == null ? null : request.body()));
    }

    private static MessageResponse message(MessageView v) {
        return new MessageResponse(
                v.id(),
                v.threadId(),
                v.authorUserId(),
                v.fromPlatform(),
                v.body(),
                v.createdAt() == null ? null : v.createdAt().toString(),
                v.authorName());
    }

    private static ThreadResponse thread(ThreadView v) {
        return new ThreadResponse(
                v.id(),
                v.userId(),
                v.fullName(),
                v.email(),
                v.lastBody(),
                v.lastAt() == null ? null : v.lastAt().toString(),
                v.unreadCount());
    }

    public record BodyRequest(String body) {}

    public record OpenRequest(UUID userId) {}

    public record UnreadResponse(long unreadCount) {}

    public record MessageResponse(
            UUID id,
            UUID threadId,
            UUID authorUserId,
            boolean fromPlatform,
            String body,
            String createdAt,
            String authorName
    ) {}

    public record ThreadResponse(
            UUID id,
            UUID userId,
            String fullName,
            String email,
            String lastBody,
            String lastAt,
            long unreadCount
    ) {}
}
