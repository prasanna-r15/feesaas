package com.feesaas.catalog.infra;

import com.github.f4b6a3.uuid.UuidCreator;
import java.sql.Date;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CatalogRepository {

    private final JdbcClient jdbc;

    public CatalogRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<BranchRow> listBranches(UUID tenantId) {
        return jdbc.sql("""
                select id, name, address, phone, is_primary, created_at
                  from tenant_branches
                 where tenant_id = :tenant and deleted_at is null
                 order by is_primary desc, lower(name)
                """)
                .param("tenant", tenantId)
                .query(this::mapBranch)
                .list();
    }

    public UUID insertBranch(UUID tenantId, String name, String address, String phone, boolean primary) {
        UUID id = UuidCreator.getTimeOrderedEpoch();
        jdbc.sql("""
                insert into tenant_branches (id, tenant_id, name, address, phone, is_primary)
                values (:id, :tenant, :name, :address, :phone, :primary)
                """)
                .param("id", id)
                .param("tenant", tenantId)
                .param("name", name)
                .param("address", address, Types.VARCHAR)
                .param("phone", phone, Types.VARCHAR)
                .param("primary", primary)
                .update();
        return id;
    }

    public int updateBranch(UUID tenantId, UUID id, String name, String address, String phone, Boolean primary) {
        return jdbc.sql("""
                update tenant_branches
                   set name = coalesce(:name, name),
                       address = coalesce(:address, address),
                       phone = coalesce(:phone, phone),
                       is_primary = coalesce(:primary, is_primary),
                       updated_at = now(),
                       version = version + 1
                 where id = :id and tenant_id = :tenant and deleted_at is null
                """)
                .param("name", name, Types.VARCHAR)
                .param("address", address, Types.VARCHAR)
                .param("phone", phone, Types.VARCHAR)
                .param("primary", primary, Types.BOOLEAN)
                .param("id", id)
                .param("tenant", tenantId)
                .update();
    }

    public int softDeleteBranch(UUID tenantId, UUID id) {
        jdbc.sql("update customers set branch_id = null where tenant_id = :tenant and branch_id = :id")
                .param("tenant", tenantId)
                .param("id", id)
                .update();
        return jdbc.sql("""
                update tenant_branches
                   set deleted_at = now(), updated_at = now(), version = version + 1
                 where id = :id and tenant_id = :tenant and deleted_at is null
                """)
                .param("id", id)
                .param("tenant", tenantId)
                .update();
    }

    public List<AddonRow> listAddons(UUID tenantId) {
        return jdbc.sql("""
                select id, name, description, amount_minor, currency, active
                  from addon_products
                 where tenant_id = :tenant and deleted_at is null
                 order by lower(name)
                """)
                .param("tenant", tenantId)
                .query((rs, i) -> new AddonRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getLong("amount_minor"),
                        rs.getString("currency"),
                        rs.getBoolean("active")))
                .list();
    }

    public Optional<AddonRow> findAddon(UUID tenantId, UUID id) {
        return jdbc.sql("""
                select id, name, description, amount_minor, currency, active
                  from addon_products
                 where id = :id and tenant_id = :tenant and deleted_at is null
                """)
                .param("id", id)
                .param("tenant", tenantId)
                .query((rs, i) -> new AddonRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getLong("amount_minor"),
                        rs.getString("currency"),
                        rs.getBoolean("active")))
                .optional();
    }

    public UUID insertAddon(UUID tenantId, String name, String description, long amountMinor, String currency) {
        UUID id = UuidCreator.getTimeOrderedEpoch();
        jdbc.sql("""
                insert into addon_products (id, tenant_id, name, description, amount_minor, currency)
                values (:id, :tenant, :name, :desc, :amount, :currency)
                """)
                .param("id", id)
                .param("tenant", tenantId)
                .param("name", name)
                .param("desc", description, Types.VARCHAR)
                .param("amount", amountMinor)
                .param("currency", currency)
                .update();
        return id;
    }

    public int updateAddon(UUID tenantId, UUID id, String name, String description, Long amountMinor, Boolean active) {
        return jdbc.sql("""
                update addon_products
                   set name = coalesce(:name, name),
                       description = coalesce(:desc, description),
                       amount_minor = coalesce(:amount, amount_minor),
                       active = coalesce(:active, active),
                       updated_at = now(),
                       version = version + 1
                 where id = :id and tenant_id = :tenant and deleted_at is null
                """)
                .param("name", name, Types.VARCHAR)
                .param("desc", description, Types.VARCHAR)
                .param("amount", amountMinor, Types.BIGINT)
                .param("active", active, Types.BOOLEAN)
                .param("id", id)
                .param("tenant", tenantId)
                .update();
    }

    public int softDeleteAddon(UUID tenantId, UUID id) {
        return jdbc.sql("""
                update addon_products
                   set deleted_at = now(), updated_at = now(), version = version + 1, active = false
                 where id = :id and tenant_id = :tenant and deleted_at is null
                """)
                .param("id", id)
                .param("tenant", tenantId)
                .update();
    }

    public UUID insertSale(
            UUID tenantId, UUID productId, UUID customerId, long amountMinor, String currency,
            String method, String notes, LocalDate soldOn, UUID createdBy) {
        UUID id = UuidCreator.getTimeOrderedEpoch();
        jdbc.sql("""
                insert into addon_sales
                    (id, tenant_id, product_id, customer_id, amount_minor, currency, method, notes, sold_on, created_by)
                values
                    (:id, :tenant, :product, :customer, :amount, :currency, :method, :notes, :soldOn, :by)
                """)
                .param("id", id)
                .param("tenant", tenantId)
                .param("product", productId)
                .param("customer", customerId)
                .param("amount", amountMinor)
                .param("currency", currency)
                .param("method", method)
                .param("notes", notes, Types.VARCHAR)
                .param("soldOn", Date.valueOf(soldOn), Types.DATE)
                .param("by", createdBy)
                .update();
        return id;
    }

    public List<SaleRow> listSales(UUID tenantId) {
        return jdbc.sql("""
                select s.id, s.product_id, p.name as product_name, s.customer_id, c.full_name as customer_name,
                       s.amount_minor, s.currency, s.method, s.notes, s.sold_on, s.created_at
                  from addon_sales s
                  join addon_products p on p.id = s.product_id
                  join customers c on c.id = s.customer_id
                 where s.tenant_id = :tenant
                 order by s.sold_on desc, s.created_at desc
                 limit 100
                """)
                .param("tenant", tenantId)
                .query((rs, i) -> new SaleRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("product_id", UUID.class),
                        rs.getString("product_name"),
                        rs.getObject("customer_id", UUID.class),
                        rs.getString("customer_name"),
                        rs.getLong("amount_minor"),
                        rs.getString("currency"),
                        rs.getString("method"),
                        rs.getString("notes"),
                        rs.getDate("sold_on").toLocalDate(),
                        rs.getTimestamp("created_at").toInstant()))
                .list();
    }

    public List<DietRow> listDiets(UUID tenantId) {
        return jdbc.sql("""
                select id, name, body, created_at
                  from diet_chart_templates
                 where tenant_id = :tenant and deleted_at is null
                 order by lower(name)
                """)
                .param("tenant", tenantId)
                .query((rs, i) -> new DietRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getString("body"),
                        rs.getTimestamp("created_at").toInstant()))
                .list();
    }

    public Optional<DietRow> findDiet(UUID tenantId, UUID id) {
        return jdbc.sql("""
                select id, name, body, created_at
                  from diet_chart_templates
                 where id = :id and tenant_id = :tenant and deleted_at is null
                """)
                .param("id", id)
                .param("tenant", tenantId)
                .query((rs, i) -> new DietRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getString("body"),
                        rs.getTimestamp("created_at").toInstant()))
                .optional();
    }

    public UUID insertDiet(UUID tenantId, String name, String body) {
        UUID id = UuidCreator.getTimeOrderedEpoch();
        jdbc.sql("""
                insert into diet_chart_templates (id, tenant_id, name, body)
                values (:id, :tenant, :name, :body)
                """)
                .param("id", id)
                .param("tenant", tenantId)
                .param("name", name)
                .param("body", body)
                .update();
        return id;
    }

    public int updateDiet(UUID tenantId, UUID id, String name, String body) {
        return jdbc.sql("""
                update diet_chart_templates
                   set name = coalesce(:name, name),
                       body = coalesce(:body, body),
                       updated_at = now(),
                       version = version + 1
                 where id = :id and tenant_id = :tenant and deleted_at is null
                """)
                .param("name", name, Types.VARCHAR)
                .param("body", body, Types.VARCHAR)
                .param("id", id)
                .param("tenant", tenantId)
                .update();
    }

    public int softDeleteDiet(UUID tenantId, UUID id) {
        return jdbc.sql("""
                update diet_chart_templates
                   set deleted_at = now(), updated_at = now(), version = version + 1
                 where id = :id and tenant_id = :tenant and deleted_at is null
                """)
                .param("id", id)
                .param("tenant", tenantId)
                .update();
    }

    public Optional<BranchRow> findBranchByName(UUID tenantId, String name) {
        return jdbc.sql("""
                select id, name, address, phone, is_primary, created_at
                  from tenant_branches
                 where tenant_id = :tenant and deleted_at is null and lower(name) = lower(:name)
                 limit 1
                """)
                .param("tenant", tenantId)
                .param("name", name)
                .query(this::mapBranch)
                .optional();
    }

    public List<MemberContact> listMembers(UUID tenantId, UUID branchId) {
        return jdbc.sql("""
                select id, full_name, phone, has_whatsapp
                  from customers
                 where tenant_id = :tenant
                   and deleted_at is null
                   and (:branchId::uuid is null or branch_id = :branchId)
                 order by full_name
                 limit 500
                """)
                .param("tenant", tenantId)
                .param("branchId", branchId, java.sql.Types.OTHER)
                .query((rs, i) -> new MemberContact(
                        rs.getObject("id", UUID.class),
                        rs.getString("full_name"),
                        rs.getString("phone"),
                        rs.getBoolean("has_whatsapp")))
                .list();
    }

    public Optional<MemberContact> findMember(UUID tenantId, UUID customerId) {
        return jdbc.sql("""
                select id, full_name, phone, has_whatsapp
                  from customers
                 where id = :id and tenant_id = :tenant and deleted_at is null
                """)
                .param("id", customerId)
                .param("tenant", tenantId)
                .query((rs, i) -> new MemberContact(
                        rs.getObject("id", UUID.class),
                        rs.getString("full_name"),
                        rs.getString("phone"),
                        rs.getBoolean("has_whatsapp")))
                .optional();
    }

    public void insertDietSend(UUID tenantId, UUID templateId, UUID customerId, String channel, String body, UUID createdBy) {
        jdbc.sql("""
                insert into diet_chart_sends (id, tenant_id, template_id, customer_id, channel, body, created_by)
                values (:id, :tenant, :template, :customer, :channel, :body, :by)
                """)
                .param("id", UuidCreator.getTimeOrderedEpoch())
                .param("tenant", tenantId)
                .param("template", templateId)
                .param("customer", customerId)
                .param("channel", channel)
                .param("body", body)
                .param("by", createdBy)
                .update();
    }

    private BranchRow mapBranch(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        return new BranchRow(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("address"),
                rs.getString("phone"),
                rs.getBoolean("is_primary"),
                rs.getTimestamp("created_at").toInstant());
    }

    public record BranchRow(UUID id, String name, String address, String phone, boolean primary, Instant createdAt) {}

    public record AddonRow(UUID id, String name, String description, long amountMinor, String currency, boolean active) {}

    public record SaleRow(
            UUID id, UUID productId, String productName, UUID customerId, String customerName,
            long amountMinor, String currency, String method, String notes, LocalDate soldOn, Instant createdAt) {}

    public record DietRow(UUID id, String name, String body, Instant createdAt) {}

    public record MemberContact(UUID id, String fullName, String phone, boolean hasWhatsapp) {}
}
