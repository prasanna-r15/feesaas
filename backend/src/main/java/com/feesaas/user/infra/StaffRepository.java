package com.feesaas.user.infra;

import com.feesaas.user.application.OwnerProvisioner;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class StaffRepository implements OwnerProvisioner {

    private final JdbcClient jdbc;

    public StaffRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UUID createOwner(UUID tenantId, String email, String phone, String fullName, String passwordHash) {
        return insert(tenantId, email, phone, fullName, passwordHash, "BUSINESS_OWNER", "ACTIVE");
    }

    public UUID insert(
            UUID tenantId,
            String email,
            String phone,
            String fullName,
            String passwordHash,
            String roleCode,
            String status) {
        UUID id = UuidCreator.getTimeOrderedEpoch();
        jdbc.sql("""
                insert into users (id, tenant_id, email, phone, full_name, password_hash, role_code, status)
                values (:id, :tenantId, :email, :phone, :name, :hash, :role, :status)
                """)
                .param("id", id)
                .param("tenantId", tenantId)
                .param("email", email)
                .param("phone", phone)
                .param("name", fullName)
                .param("hash", passwordHash)
                .param("role", roleCode)
                .param("status", status)
                .update();
        jdbc.sql("select auth_ensure_personal_workspace(:id)")
                .param("id", id)
                .query(UUID.class)
                .optional();
        jdbc.sql("select seed_personal_categories(auth_workspace_of(:id))")
                .param("id", id)
                .query(Integer.class)
                .optional();
        if (tenantId != null) {
            jdbc.sql("select auth_attach_business_membership(:id, :tenantId, :role)")
                    .param("id", id)
                    .param("tenantId", tenantId)
                    .param("role", roleCode)
                    .query(Integer.class)
                    .optional();
        }
        return id;
    }

    public int convertIndividualToStaff(UUID userId, UUID tenantId, String fullName) {
        return convertIndividualToRole(userId, tenantId, fullName, "STAFF");
    }

    public int convertIndividualToRole(UUID userId, UUID tenantId, String fullName, String roleCode) {
        String role = "BUSINESS_OWNER".equals(roleCode) ? "BUSINESS_OWNER" : "STAFF";
        return jdbc.sql("""
                update users
                   set tenant_id = :tenantId,
                       role_code = :role,
                       full_name = coalesce(:name, full_name),
                       updated_at = now()
                 where id = :id
                   and role_code = 'INDIVIDUAL'
                   and tenant_id is null
                """)
                .param("tenantId", tenantId)
                .param("role", role)
                .param("name", fullName)
                .param("id", userId)
                .update();
    }

    public long countStaff() {
        return jdbc.sql("select count(*) from users where role_code = 'STAFF' and status <> 'DISABLED'")
                .query(Long.class)
                .single();
    }

    public Optional<StaffRow> findStaffById(UUID id) {
        return jdbc.sql("""
                select id, tenant_id, email, phone, full_name, role_code, status, created_at
                  from users
                 where id = :id and role_code = 'STAFF'
                """)
                .param("id", id)
                .query(StaffRepository::map)
                .optional();
    }

    public List<StaffRow> listStaff() {
        return jdbc.sql("""
                select id, tenant_id, email, phone, full_name, role_code, status, created_at
                  from users
                 where role_code = 'STAFF'
                 order by created_at
                """)
                .query(StaffRepository::map)
                .list();
    }

    public int updateStaff(UUID id, String fullName, String email, String phone, String status) {
        return jdbc.sql("""
                update users
                   set full_name = coalesce(:name, full_name),
                       email = coalesce(:email, email),
                       phone = coalesce(:phone, phone),
                       status = coalesce(:status, status),
                       updated_at = now(),
                       version = version + 1
                 where id = :id and role_code = 'STAFF'
                """)
                .param("name", fullName)
                .param("email", email)
                .param("phone", phone)
                .param("status", status)
                .param("id", id)
                .update();
    }

    public int disable(UUID id) {
        return jdbc.sql("""
                update users
                   set status = 'DISABLED',
                       token_version = token_version + 1,
                       updated_at = now(),
                       version = version + 1
                 where id = :id and role_code = 'STAFF'
                """)
                .param("id", id)
                .update();
    }

    public void replacePermissions(UUID tenantId, UUID userId, List<String> permissions) {
        jdbc.sql("delete from user_permissions where user_id = :id").param("id", userId).update();
        for (String code : permissions) {
            jdbc.sql("""
                    insert into user_permissions (tenant_id, user_id, permission_code)
                    values (:tenantId, :userId, :code)
                    """)
                    .param("tenantId", tenantId)
                    .param("userId", userId)
                    .param("code", code)
                    .update();
        }
    }

    public List<String> permissionsOf(UUID userId) {
        return jdbc.sql("select permission_code from user_permissions where user_id = :id order by permission_code")
                .param("id", userId)
                .query(String.class)
                .list();
    }

    public List<String> staffRoleDefaults() {
        return jdbc.sql("""
                select permission_code from role_permissions
                 where role_code = 'STAFF'
                 order by permission_code
                """)
                .query(String.class)
                .list();
    }

    public List<PermissionRow> tenantPermissionCatalogue() {
        return jdbc.sql("""
                select code, module, description from permissions
                 where module <> 'PLATFORM'
                 order by module, code
                """)
                .query((rs, i) -> new PermissionRow(
                        rs.getString("code"), rs.getString("module"), rs.getString("description")))
                .list();
    }

    public boolean permissionExists(String code) {
        Long count = jdbc.sql("select count(*) from permissions where code = :code and module <> 'PLATFORM'")
                .param("code", code)
                .query(Long.class)
                .single();
        return count != null && count > 0;
    }

    public void revokeRefreshTokens(UUID userId) {
        jdbc.sql("update refresh_tokens set revoked_at = now() where user_id = :id and revoked_at is null")
                .param("id", userId)
                .update();
    }

    private static StaffRow map(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        return new StaffRow(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getString("email"),
                rs.getString("phone"),
                rs.getString("full_name"),
                rs.getString("role_code"),
                rs.getString("status"),
                rs.getTimestamp("created_at").toInstant());
    }

    public record StaffRow(
            UUID id,
            UUID tenantId,
            String email,
            String phone,
            String fullName,
            String role,
            String status,
            java.time.Instant createdAt
    ) {}

    public record PermissionRow(String code, String module, String description) {}
}
