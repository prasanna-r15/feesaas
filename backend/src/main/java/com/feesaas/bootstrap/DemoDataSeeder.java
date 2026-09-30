package com.feesaas.bootstrap;

import com.feesaas.configuration.infra.PresetRepository;
import com.feesaas.shared.tenancy.TenantContext;
import com.feesaas.shared.tenancy.TenantExecutor;
import com.feesaas.shared.tenancy.TenantScope;
import com.feesaas.tenant.infra.TenantRepository;
import com.feesaas.user.application.OwnerProvisioner;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Order(100)
@ConditionalOnProperty(name = "feesaas.seed-demo", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    public static final String OWNER_EMAIL = "owner@demo.local";
    public static final String OWNER_PHONE = "+919999999999";
    public static final String ADMIN_EMAIL = "admin@feesaas.local";
    public static final String DEMO_PASSWORD = "welcome123";

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final JdbcClient jdbc;
    private final TenantExecutor tenants;
    private final TenantRepository tenantRepository;
    private final PresetRepository presets;
    private final OwnerProvisioner owners;
    private final PasswordEncoder passwords;

    public DemoDataSeeder(
            JdbcClient jdbc,
            TenantExecutor tenants,
            TenantRepository tenantRepository,
            PresetRepository presets,
            OwnerProvisioner owners,
            PasswordEncoder passwords) {
        this.jdbc = jdbc;
        this.tenants = tenants;
        this.tenantRepository = tenantRepository;
        this.presets = presets;
        this.owners = owners;
        this.passwords = passwords;
    }

    @Override
    public void run(ApplicationArguments args) {
        tenants.run(TenantScope.platformAdmin(), this::seed);
    }

    private void seed() {
        try {
            seedGymOwner();
            seedPlatformAdmin();
        } catch (DataIntegrityViolationException e) {
            log.warn("Demo seed skipped because a unique value already exists: {}", e.getMostSpecificCause().getMessage());
        }

        jdbc.sql("select id from tenants where slug = 'demo-gym'")
                .query(UUID.class)
                .optional()
                .ifPresent(id -> {
                    jdbc.sql("""
                            update tenant_settings
                               set phone = coalesce(phone, :phone),
                                   whatsapp_number = coalesce(whatsapp_number, :phone)
                             where tenant_id = :id
                            """)
                            .param("id", id)
                            .param("phone", OWNER_PHONE)
                            .update();
                    jdbc.sql("""
                            insert into tenant_modules (tenant_id, module_code, enabled)
                            values (:id, 'ATTENDANCE', true)
                            on conflict (tenant_id, module_code) do update set enabled = true
                            """)
                            .param("id", id)
                            .update();
                    tenants.run(TenantScope.tenant(id), this::seedDemoCustomers);
                });
    }

    private void seedGymOwner() {
        boolean gymExists = jdbc.sql("select count(*) from tenants where slug = 'demo-gym'")
                .query(Long.class)
                .single() > 0;
        boolean ownerTaken = jdbc.sql("""
                select count(*) from users
                 where lower(email) = lower(:email) or phone = :phone
                """)
                .param("email", OWNER_EMAIL)
                .param("phone", OWNER_PHONE)
                .query(Long.class)
                .single() > 0;
        if (gymExists || ownerTaken) {
            return;
        }
        var preset = presets.findByCode("GYM").orElseThrow();
        UUID tenantId = tenantRepository.insert(
                "Demo Gym", "demo-gym", "GYM", "Asia/Kolkata", "INR");
        tenantRepository.insertSettings(tenantId, preset.labelsJson());
        List<String> modules = List.of(
                "CUSTOMERS", "FEES", "PAYMENTS", "MEMBERSHIP", "ATTENDANCE",
                "NOTIFICATIONS", "REPORTS", "SETTINGS", "BRANCHES", "ADDONS", "DIET_CHARTS");
        for (String module : modules) {
            tenantRepository.insertModule(tenantId, module);
        }
        jdbc.sql("update tenants set status = 'ACTIVE' where id = :id")
                .param("id", tenantId)
                .update();
        owners.createOwner(
                tenantId, OWNER_EMAIL, OWNER_PHONE, "Demo Owner", passwords.encode(DEMO_PASSWORD));
        log.info("Seeded demo owner {} / {}", OWNER_EMAIL, DEMO_PASSWORD);
    }

    private void seedPlatformAdmin() {
        boolean hasAdmin = jdbc.sql("select count(*) from users where lower(email) = lower(:e)")
                .param("e", ADMIN_EMAIL)
                .query(Long.class)
                .single() > 0;
        if (hasAdmin) {
            return;
        }
        jdbc.sql("""
                insert into users (id, tenant_id, email, full_name, password_hash, role_code, status)
                values (:id, null, :email, 'Platform Admin', :hash, 'PLATFORM_SUPER_ADMIN', 'ACTIVE')
                """)
                .param("id", UUID.randomUUID())
                .param("email", ADMIN_EMAIL)
                .param("hash", passwords.encode(DEMO_PASSWORD))
                .update();
        log.info("Seeded platform admin {} / {}", ADMIN_EMAIL, DEMO_PASSWORD);
    }

    private void seedDemoCustomers() {
        jdbc.sql("update customers set due_date = current_date + 14 where due_date is null and deleted_at is null")
                .update();
        boolean any = jdbc.sql("select count(*) from customers where deleted_at is null")
                .query(Long.class)
                .single() > 0;
        if (any) {
            return;
        }
        UUID tenantId = TenantContext.requireTenantId();
        insertCustomer(tenantId, "Rahul Sharma", "+919876543210", "rahul@demo.local", java.time.LocalDate.now().plusDays(7), true);
        insertCustomer(tenantId, "Anita Desai", "+919876543211", "anita@demo.local", java.time.LocalDate.now().plusDays(21), false);
        log.info("Seeded demo customers for Demo Gym");
    }

    private void insertCustomer(
            UUID tenantId, String name, String phone, String email, java.time.LocalDate dueDate, boolean hasWhatsapp) {
        long seq = jdbc.sql("select next_counter('customer')").query(Long.class).single();
        jdbc.sql("""
                insert into customers (id, tenant_id, customer_code, full_name, phone, email, due_date, has_whatsapp)
                values (:id, :tenantId, :code, :name, :phone, :email, :dueDate, :hasWa)
                """)
                .param("id", UUID.randomUUID())
                .param("tenantId", tenantId)
                .param("code", "C" + String.format("%04d", seq))
                .param("name", name)
                .param("phone", phone)
                .param("email", email)
                .param("dueDate", java.sql.Date.valueOf(dueDate))
                .param("hasWa", hasWhatsapp)
                .update();
    }
}
