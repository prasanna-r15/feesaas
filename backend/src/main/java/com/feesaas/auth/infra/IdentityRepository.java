package com.feesaas.auth.infra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class IdentityRepository {

    private final JdbcClient jdbc;

    public IdentityRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public UUID registerIndividual(String fullName, String email, String phone, String passwordHash) {
        try {
            return jdbc.sql("select auth_register_individual(:name, :email, :phone, :hash)")
                    .param("name", fullName)
                    .param("email", email)
                    .param("phone", phone)
                    .param("hash", passwordHash)
                    .query(UUID.class)
                    .single();
        } catch (DuplicateKeyException e) {
            throw e;
        }
    }

    public void seedCategories(UUID workspaceId) {
        jdbc.sql("select seed_personal_categories(:id)").param("id", workspaceId).query(Integer.class).single();
    }

    public Optional<UUID> personalWorkspaceId(UUID userId) {
        return jdbc.sql("select auth_workspace_of(:id)")
                .param("id", userId)
                .query(UUID.class)
                .optional();
    }

    public Optional<ProfileRow> profile(UUID userId) {
        return jdbc.sql("""
                select user_id, default_kind, default_tenant_id, default_workspace_id, default_group_id, onboarding_done
                  from user_profiles
                 where user_id = :id
                """)
                .param("id", userId)
                .query((rs, i) -> new ProfileRow(
                        rs.getObject("user_id", UUID.class),
                        rs.getString("default_kind"),
                        rs.getObject("default_tenant_id", UUID.class),
                        rs.getObject("default_workspace_id", UUID.class),
                        rs.getObject("default_group_id", UUID.class),
                        rs.getBoolean("onboarding_done")))
                .optional();
    }

    public void upsertProfileDefault(UUID userId, String kind, UUID tenantId, UUID workspaceId, UUID groupId) {
        jdbc.sql("""
                insert into user_profiles (user_id, default_kind, default_tenant_id, default_workspace_id, default_group_id, onboarding_done)
                values (:userId, :kind, :tenantId, :ws, :groupId, true)
                on conflict (user_id) do update set
                  default_kind = excluded.default_kind,
                  default_tenant_id = excluded.default_tenant_id,
                  default_workspace_id = excluded.default_workspace_id,
                  default_group_id = excluded.default_group_id,
                  updated_at = now()
                """)
                .param("userId", userId)
                .param("kind", kind)
                .param("tenantId", tenantId)
                .param("ws", workspaceId)
                .param("groupId", groupId)
                .update();
    }

    public void completeOnboarding(UUID userId) {
        jdbc.sql("update user_profiles set onboarding_done = true, updated_at = now() where user_id = :id")
                .param("id", userId)
                .update();
    }

    public List<ContextRow> memberships(UUID userId) {
        return jdbc.sql("""
                select m.kind, m.role_code, m.business_tenant_id, m.workspace_id, m.group_id,
                       t.name as tenant_name, w.name as workspace_name, g.name as group_name
                  from user_memberships m
                  left join tenants t on t.id = m.business_tenant_id
                  left join personal_workspaces w on w.id = m.workspace_id
                  left join expense_groups g on g.id = m.group_id
                 where m.user_id = :id
                 order by m.created_at
                """)
                .param("id", userId)
                .query((rs, i) -> new ContextRow(
                        rs.getString("kind"),
                        rs.getString("role_code"),
                        rs.getObject("business_tenant_id", UUID.class),
                        rs.getObject("workspace_id", UUID.class),
                        rs.getObject("group_id", UUID.class),
                        rs.getString("tenant_name"),
                        rs.getString("workspace_name"),
                        rs.getString("group_name")))
                .list();
    }

    public record ProfileRow(
            UUID userId,
            String defaultKind,
            UUID defaultTenantId,
            UUID defaultWorkspaceId,
            UUID defaultGroupId,
            boolean onboardingDone
    ) {}

    public record ContextRow(
            String kind,
            String roleCode,
            UUID tenantId,
            UUID workspaceId,
            UUID groupId,
            String tenantName,
            String workspaceName,
            String groupName
    ) {}
}
