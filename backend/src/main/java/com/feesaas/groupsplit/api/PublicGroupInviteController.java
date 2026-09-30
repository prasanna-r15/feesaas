package com.feesaas.groupsplit.api;

import com.feesaas.groupsplit.application.GroupSplitService;
import com.feesaas.groupsplit.infra.GroupSplitRepository.InvitePreview;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/group-invites")
public class PublicGroupInviteController {

    private final GroupSplitService groups;

    public PublicGroupInviteController(GroupSplitService groups) {
        this.groups = groups;
    }

    @GetMapping("/{token}")
    public InvitePreview preview(@PathVariable String token) {
        return groups.previewInvite(token);
    }
}
