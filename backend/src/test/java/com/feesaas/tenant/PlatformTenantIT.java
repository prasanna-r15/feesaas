package com.feesaas.tenant;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.feesaas.support.AbstractPostgresIT;
import com.feesaas.support.TestAuth;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

class PlatformTenantIT extends AbstractPostgresIT {

    @Autowired MockMvc mvc;
    @Autowired PasswordEncoder encoder;

    @Test
    void platformCreatesTenantAndOwnerCanLogin() throws Exception {
        String platformToken = platformToken();
        String slug = "gym-" + UUID.randomUUID().toString().substring(0, 8);
        String ownerEmail = "owner-" + slug + "@example.com";

        String created = mvc.perform(post("/api/v1/platform/tenants")
                        .header("Authorization", "Bearer " + platformToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Fit Zone",
                                  "slug": "%s",
                                  "businessType": "GYM",
                                  "owner": {
                                    "fullName": "Asha Owner",
                                    "email": "%s",
                                    "password": "%s"
                                  }
                                }
                                """.formatted(slug, ownerEmail, TestAuth.PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value(slug))
                .andExpect(jsonPath("$.businessType").value("GYM"))
                .andExpect(jsonPath("$.modules").isArray())
                .andExpect(jsonPath("$.ownerId").isNotEmpty())
                .andExpect(jsonPath("$.planCode").value("FREE"))
                .andReturn().getResponse().getContentAsString();
        String tenantId = TestAuth.jsonField(created, "id");

        TestAuth.login(mvc, ownerEmail, TestAuth.PASSWORD);

        mvc.perform(get("/api/v1/platform/tenants/" + tenantId)
                        .header("Authorization", "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Fit Zone"));
    }

    @Test
    void tenantOwnerCannotCallPlatformRoutes() throws Exception {
        String ownerEmail = seedOwnerEmail();
        String ownerToken = TestAuth.login(mvc, ownerEmail, TestAuth.PASSWORD);
        mvc.perform(get("/api/v1/platform/tenants").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void suspendBlocksOwnerLoginAndActivateRestoresIt() throws Exception {
        String platformToken = platformToken();
        String slug = "s-" + UUID.randomUUID().toString().substring(0, 8);
        String ownerEmail = "owner-" + slug + "@example.com";
        String created = mvc.perform(post("/api/v1/platform/tenants")
                        .header("Authorization", "Bearer " + platformToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"S","slug":"%s","businessType":"GENERIC",
                                 "owner":{"fullName":"O","email":"%s","password":"%s"}}
                                """.formatted(slug, ownerEmail, TestAuth.PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String tenantId = TestAuth.jsonField(created, "id");

        mvc.perform(post("/api/v1/platform/tenants/" + tenantId + "/suspend")
                        .header("Authorization", "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));

        mvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
                        .content(TestAuth.loginJson(ownerEmail, TestAuth.PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_SUSPENDED"));

        mvc.perform(post("/api/v1/platform/tenants/" + tenantId + "/activate")
                        .header("Authorization", "Bearer " + platformToken))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        TestAuth.login(mvc, ownerEmail, TestAuth.PASSWORD);
    }

    @Test
    void adminCanUpdateLogoAndOwnerSeesItOnBootstrap() throws Exception {
        String platformToken = platformToken();
        String slug = "logo-" + UUID.randomUUID().toString().substring(0, 8);
        String ownerEmail = "owner-" + slug + "@example.com";
        String png = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+ip1sAAAAASUVORK5CYII=";
        String created = mvc.perform(post("/api/v1/platform/tenants")
                        .header("Authorization", "Bearer " + platformToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"Logo Gym","slug":"%s","businessType":"GYM",
                                 "logoBase64":"data:image/png;base64,%s",
                                 "owner":{"fullName":"O","email":"%s","password":"%s"}}
                                """.formatted(slug, png, ownerEmail, TestAuth.PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hasLogo").value(true))
                .andReturn().getResponse().getContentAsString();
        String tenantId = TestAuth.jsonField(created, "id");

        mvc.perform(get("/api/v1/platform/tenants/" + tenantId)
                        .header("Authorization", "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasLogo").value(true))
                .andExpect(jsonPath("$.logoBase64").value(org.hamcrest.Matchers.startsWith("data:image/png;base64,")));

        mvc.perform(patch("/api/v1/platform/tenants/" + tenantId)
                        .header("Authorization", "Bearer " + platformToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Logo Gym Renamed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Logo Gym Renamed"))
                .andExpect(jsonPath("$.hasLogo").value(true));

        String ownerToken = TestAuth.login(mvc, ownerEmail, TestAuth.PASSWORD);
        mvc.perform(get("/api/v1/me/bootstrap").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenant.logoBase64").value(org.hamcrest.Matchers.startsWith("data:image/png;base64,")));

        mvc.perform(delete("/api/v1/platform/tenants/" + tenantId)
                        .header("Authorization", "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    private String platformToken() throws Exception {
        String email = "admin-" + UUID.randomUUID() + "@feesaas.local";
        ownerJdbc().update("""
                insert into users(id, tenant_id, email, full_name, password_hash, role_code, status)
                values (?, null, ?, 'Platform', ?, 'PLATFORM_SUPER_ADMIN', 'ACTIVE')
                """, UUID.randomUUID(), email, encoder.encode(TestAuth.PASSWORD));
        return TestAuth.login(mvc, email, TestAuth.PASSWORD);
    }

    private String seedOwnerEmail() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String email = "owner-" + userId + "@example.com";
        var owner = ownerJdbc();
        owner.update("insert into tenants(id, name, slug, business_type, status) values (?, ?, ?, 'GYM', 'ACTIVE')",
                tenantId, "T", "t-" + tenantId);
        owner.update("insert into tenant_settings(tenant_id) values (?)", tenantId);
        owner.update("""
                insert into users(id, tenant_id, email, full_name, password_hash, role_code, status)
                values (?, ?, ?, 'Owner', ?, 'BUSINESS_OWNER', 'ACTIVE')
                """, userId, tenantId, email, encoder.encode(TestAuth.PASSWORD));
        return email;
    }
}
