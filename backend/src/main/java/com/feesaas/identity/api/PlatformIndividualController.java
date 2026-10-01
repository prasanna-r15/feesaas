package com.feesaas.identity.api;

import com.feesaas.identity.application.IndividualDirectoryService;
import com.feesaas.identity.application.IndividualDirectoryService.IndividualView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/individuals")
public class PlatformIndividualController {

    private final IndividualDirectoryService directory;

    public PlatformIndividualController(IndividualDirectoryService directory) {
        this.directory = directory;
    }

    @GetMapping
    public List<IndividualResponse> list(@RequestParam(required = false) String q) {
        return directory.list(q).stream().map(PlatformIndividualController::toResponse).toList();
    }

    private static IndividualResponse toResponse(IndividualView view) {
        return new IndividualResponse(
                view.id(),
                view.fullName(),
                view.email(),
                view.phone(),
                view.status(),
                view.lastLoginAt(),
                view.createdAt(),
                view.groupMemberCount(),
                view.groupsJoined(),
                view.expenseCount());
    }

    public record IndividualResponse(
            UUID id,
            String fullName,
            String email,
            String phone,
            String status,
            Instant lastLoginAt,
            Instant createdAt,
            long groupMemberCount,
            long groupsJoined,
            long expenseCount
    ) {}
}
