package com.feesaas.tenant.infra;

import com.github.f4b6a3.uuid.UuidCreator;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class TenantRepository {

    private final JdbcClient jdbc;

    public TenantRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public UUID insert(String name, String slug, String businessType, String timezone, String currency) {
        UUID id = UuidCreator.getTimeOrderedEpoch();
        jdbc.sql("""
                insert into tenants (id, name, slug, business_type, timezone, currency, status, plan_code)
                values (:id, :name, :slug, :type, :tz, :currency, 'ONBOARDING', 'FREE')
                """)
                .param("id", id)
                .param("name", name)
                .param("slug", slug)
                .param("type", businessType)
                .param("tz", timezone)
                .param("currency", currency)
                .update();
        return id;
    }

    public void insertSettings(UUID tenantId, String labelsJson) {
        jdbc.sql("insert into tenant_settings (tenant_id, labels) values (:id, cast(:labels as jsonb))")
                .param("id", tenantId)
                .param("labels", labelsJson)
                .update();
    }

    public void insertModule(UUID tenantId, String moduleCode) {
        jdbc.sql("""
                insert into tenant_modules (tenant_id, module_code, enabled)
                values (:id, :module, true)
                """)
                .param("id", tenantId)
                .param("module", moduleCode)
                .update();
    }

    private static final String TENANT_SELECT = """
            select t.id, t.name, t.slug, t.business_type, t.status, t.timezone, t.currency, t.created_at,
                   t.plan_code,
                   coalesce(t.custom_max_members, p.max_members) as max_members,
                   coalesce(t.custom_max_staff, p.max_staff) as max_staff,
                   (s.logo_base64 is not null and char_length(s.logo_base64) > 0) as has_logo,
                   s.logo_base64,
                   coalesce(nullif(t.display_name, ''), t.name) as display_name,
                   t.accent_color, t.trial_ends_at, t.grace_days, t.billing_status,
                   t.custom_max_members, t.custom_max_staff,
                   s.phone, s.whatsapp_number,
                   (select count(*) from customers c where c.tenant_id = t.id and c.deleted_at is null) as member_count,
                   (select count(*) from users u
                     where u.tenant_id = t.id and u.role_code = 'STAFF' and u.status <> 'DISABLED') as staff_count,
                   (select max(u.last_login_at) from users u where u.tenant_id = t.id) as last_login_at,
                   (select max(pay.paid_on) from payments pay
                     where pay.tenant_id = t.id and pay.voided_at is null) as last_collection_on,
                   o.id as owner_id, o.full_name as owner_full_name, o.email as owner_email, o.phone as owner_phone
              from tenants t
              join platform_plans p on p.code = t.plan_code
              left join tenant_settings s on s.tenant_id = t.id
              left join lateral (
                    select id, full_name, email, phone
                      from users
                     where tenant_id = t.id and role_code = 'BUSINESS_OWNER'
                     order by created_at
                     limit 1
              ) o on true
            """;

    public Optional<TenantRow> findById(UUID id) {
        return jdbc.sql(TENANT_SELECT + " where t.id = :id")
                .param("id", id)
                .query(TenantRepository::map)
                .optional();
    }

    public Optional<TenantRow> findBySlug(String slug) {
        return jdbc.sql(TENANT_SELECT + " where t.slug = :slug")
                .param("slug", slug)
                .query(TenantRepository::map)
                .optional();
    }

    public List<TenantRow> list() {
        return list("", "", "", "");
    }

    public List<TenantRow> list(String query, String status, String plan, String businessType) {
        String q = query == null ? "" : query.trim();
        String st = status == null ? "" : status.trim();
        String pl = plan == null ? "" : plan.trim();
        String bt = businessType == null ? "" : businessType.trim();
        return jdbc.sql(TENANT_SELECT + """
                 where (:q = ''
                        or t.name ilike '%' || :q || '%'
                        or t.slug ilike '%' || :q || '%'
                        or coalesce(t.display_name, '') ilike '%' || :q || '%')
                   and (:st = '' or t.status = :st)
                   and (:pl = '' or t.plan_code = :pl)
                   and (:bt = '' or t.business_type = :bt)
                 order by t.created_at desc
                """)
                .param("q", q)
                .param("st", st)
                .param("pl", pl)
                .param("bt", bt)
                .query(TenantRepository::map)
                .list();
    }

    public int updateProfile(UUID id, String name, String slug, String timezone, String currency) {
        return jdbc.sql("""
                update tenants
                   set name = coalesce(:name, name),
                       slug = coalesce(:slug, slug),
                       timezone = coalesce(:tz, timezone),
                       currency = coalesce(:currency, currency),
                       updated_at = now(),
                       version = version + 1
                 where id = :id
                """)
                .param("name", name)
                .param("slug", slug)
                .param("tz", timezone)
                .param("currency", currency)
                .param("id", id)
                .update();
    }

    public int updateOps(
            UUID id,
            String displayName,
            String accentColor,
            Instant trialEndsAt,
            Integer graceDays,
            String billingStatus,
            Integer customMaxMembers,
            Integer customMaxStaff) {
        return jdbc.sql("""
                update tenants
                   set display_name = coalesce(:displayName, display_name),
                       accent_color = coalesce(:accent, accent_color),
                       trial_ends_at = coalesce(:trial, trial_ends_at),
                       grace_days = coalesce(:grace, grace_days),
                       billing_status = coalesce(:billing, billing_status),
                       custom_max_members = coalesce(:cmm, custom_max_members),
                       custom_max_staff = coalesce(:cms, custom_max_staff),
                       updated_at = now(),
                       version = version + 1
                 where id = :id
                """)
                .param("displayName", displayName)
                .param("accent", accentColor)
                .param("trial", trialEndsAt == null ? null : java.sql.Timestamp.from(trialEndsAt), java.sql.Types.TIMESTAMP)
                .param("grace", graceDays, java.sql.Types.INTEGER)
                .param("billing", billingStatus)
                .param("cmm", customMaxMembers, java.sql.Types.INTEGER)
                .param("cms", customMaxStaff, java.sql.Types.INTEGER)
                .param("id", id)
                .update();
    }

    public int clearCustomLimits(UUID id) {
        return jdbc.sql("""
                update tenants
                   set custom_max_members = null, custom_max_staff = null,
                       updated_at = now(), version = version + 1
                 where id = :id
                """)
                .param("id", id)
                .update();
    }

    public void replaceModules(UUID tenantId, List<String> modules) {
        jdbc.sql("delete from tenant_modules where tenant_id = :id").param("id", tenantId).update();
        for (String module : modules) {
            insertModule(tenantId, module);
        }
    }

    public List<UUID> idsActive() {
        return jdbc.sql("select id from tenants where status = 'ACTIVE' order by name")
                .query(UUID.class)
                .list();
    }

    public List<UUID> idsPastGrace() {
        return jdbc.sql("""
                select id from tenants
                 where status = 'ACTIVE'
                   and trial_ends_at is not null
                   and plan_code = 'PRO'
                   and trial_ends_at + (grace_days * interval '1 day') < now()
                   and not exists (
                        select 1 from platform_invoices i
                         where i.tenant_id = tenants.id
                           and i.status = 'PAID'
                           and i.paid_at >= tenants.trial_ends_at
                   )
                """)
                .query(UUID.class)
                .list();
    }

    public List<UUID> idsPastTrial() {
        return jdbc.sql("""
                select id from tenants
                 where status = 'ACTIVE'
                   and trial_ends_at is not null
                   and plan_code = 'PRO'
                   and trial_ends_at < now()
                   and trial_ends_at + (grace_days * interval '1 day') >= now()
                   and billing_status = 'OK'
                """)
                .query(UUID.class)
                .list();
    }

    public int updateLogo(UUID tenantId, String logoBase64) {
        return jdbc.sql("""
                update tenant_settings
                   set logo_base64 = :logo,
                       updated_at = now(),
                       version = version + 1
                 where tenant_id = :id
                """)
                .param("logo", logoBase64, java.sql.Types.VARCHAR)
                .param("id", tenantId)
                .update();
    }

    public int updateStatus(UUID id, String status) {
        return jdbc.sql("""
                update tenants
                   set status = :status, updated_at = now(), version = version + 1
                 where id = :id
                """)
                .param("status", status)
                .param("id", id)
                .update();
    }

    public List<String> modules(UUID tenantId) {
        return jdbc.sql("select module_code from tenant_modules where tenant_id = :id and enabled = true order by module_code")
                .param("id", tenantId)
                .query(String.class)
                .list();
    }

    public Optional<ContactRow> findContact(UUID tenantId) {
        return jdbc.sql("""
                select t.name, s.phone, s.whatsapp_number
                  from tenants t
                  join tenant_settings s on s.tenant_id = t.id
                 where t.id = :id
                """)
                .param("id", tenantId)
                .query((rs, i) -> new ContactRow(
                        rs.getString("name"),
                        rs.getString("phone"),
                        rs.getString("whatsapp_number")))
                .optional();
    }

    public int updateContact(UUID tenantId, String phone, String whatsappNumber) {
        return jdbc.sql("""
                update tenant_settings
                   set phone = :phone,
                       whatsapp_number = :whatsapp,
                       updated_at = now(),
                       version = version + 1
                 where tenant_id = :id
                """)
                .param("phone", phone, java.sql.Types.VARCHAR)
                .param("whatsapp", whatsappNumber, java.sql.Types.VARCHAR)
                .param("id", tenantId)
                .update();
    }

    public int startTrialIfMissing(UUID id) {
        return jdbc.sql("""
                update tenants
                   set trial_ends_at = coalesce(trial_ends_at, now() + interval '14 days'),
                       billing_status = 'OK',
                       updated_at = now(),
                       version = version + 1
                 where id = :id
                """)
                .param("id", id)
                .update();
    }

    public int markBilling(UUID id, String billingStatus) {
        return jdbc.sql("""
                update tenants
                   set billing_status = :billing, updated_at = now(), version = version + 1
                 where id = :id
                """)
                .param("billing", billingStatus)
                .param("id", id)
                .update();
    }

    public int updatePlan(UUID id, String planCode) {
        return jdbc.sql("""
                update tenants
                   set plan_code = :plan, updated_at = now(), version = version + 1
                 where id = :id
                """)
                .param("plan", planCode)
                .param("id", id)
                .update();
    }

    public Optional<LimitsRow> findLimits(UUID tenantId) {
        return jdbc.sql("""
                select t.plan_code,
                       coalesce(t.custom_max_members, p.max_members) as max_members,
                       coalesce(t.custom_max_staff, p.max_staff) as max_staff
                  from tenants t
                  join platform_plans p on p.code = t.plan_code
                 where t.id = :id
                """)
                .param("id", tenantId)
                .query((rs, i) -> new LimitsRow(
                        rs.getString("plan_code"),
                        rs.getInt("max_members"),
                        rs.getInt("max_staff")))
                .optional();
    }

    public Optional<String> labelsJson(UUID tenantId) {
        return jdbc.sql("select labels::text from tenant_settings where tenant_id = :id")
                .param("id", tenantId)
                .query(String.class)
                .optional();
    }

    private static TenantRow map(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        java.sql.Timestamp login = rs.getTimestamp("last_login_at");
        java.sql.Date collected = rs.getDate("last_collection_on");
        java.sql.Timestamp trial = rs.getTimestamp("trial_ends_at");
        Integer cmm = (Integer) rs.getObject("custom_max_members");
        Integer cms = (Integer) rs.getObject("custom_max_staff");
        return new TenantRow(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("slug"),
                rs.getString("business_type"),
                rs.getString("status"),
                rs.getString("timezone"),
                rs.getString("currency"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getString("plan_code"),
                rs.getInt("max_members"),
                rs.getInt("max_staff"),
                rs.getBoolean("has_logo"),
                rs.getString("logo_base64"),
                rs.getString("display_name"),
                rs.getString("accent_color"),
                trial == null ? null : trial.toInstant(),
                rs.getInt("grace_days"),
                rs.getString("billing_status"),
                cmm,
                cms,
                rs.getString("phone"),
                rs.getString("whatsapp_number"),
                rs.getLong("member_count"),
                rs.getLong("staff_count"),
                login == null ? null : login.toInstant(),
                collected == null ? null : collected.toLocalDate(),
                rs.getObject("owner_id", UUID.class),
                rs.getString("owner_full_name"),
                rs.getString("owner_email"),
                rs.getString("owner_phone"));
    }

    public record TenantRow(
            UUID id,
            String name,
            String slug,
            String businessType,
            String status,
            String timezone,
            String currency,
            Instant createdAt,
            String planCode,
            int maxMembers,
            int maxStaff,
            boolean hasLogo,
            String logoBase64,
            String displayName,
            String accentColor,
            Instant trialEndsAt,
            int graceDays,
            String billingStatus,
            Integer customMaxMembers,
            Integer customMaxStaff,
            String phone,
            String whatsappNumber,
            long memberCount,
            long staffCount,
            Instant lastLoginAt,
            LocalDate lastCollectionOn,
            UUID ownerId,
            String ownerFullName,
            String ownerEmail,
            String ownerPhone
    ) {}

    public record LimitsRow(String planCode, int maxMembers, int maxStaff) {}

    public record ContactRow(String name, String phone, String whatsappNumber) {}
}
