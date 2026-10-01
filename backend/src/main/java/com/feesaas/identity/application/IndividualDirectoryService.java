package com.feesaas.identity.application;

import com.feesaas.identity.infra.IndividualDirectoryRepository;
import com.feesaas.identity.infra.IndividualDirectoryRepository.IndividualRow;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IndividualDirectoryService {

    private final IndividualDirectoryRepository directory;

    public IndividualDirectoryService(IndividualDirectoryRepository directory) {
        this.directory = directory;
    }

    @PreAuthorize("hasPermission(null, 'platform.tenants.manage')")
    @Transactional(readOnly = true)
    public List<IndividualView> list(String query) {
        return directory.list(query).stream().map(IndividualDirectoryService::toView).toList();
    }

    private static IndividualView toView(IndividualRow row) {
        return new IndividualView(
                row.id(),
                row.fullName(),
                row.email(),
                row.phone(),
                row.status(),
                row.lastLoginAt(),
                row.createdAt(),
                row.groupMemberCount(),
                row.groupsJoined(),
                row.expenseCount());
    }

    public record IndividualView(
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
