package com.feesaas.user;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.feesaas.support.AbstractPostgresIT;
import com.feesaas.support.TestAuth;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

class StaffIT extends AbstractPostgresIT {

    @Autowired MockMvc mvc;
    @Autowired PasswordEncoder encoder;

    @Test
    void ownerCreatesStaffAndStaffCannotManageStaff() throws Exception {
        Owner owner = seedOwner();
        String ownerToken = TestAuth.login(mvc, owner.email, TestAuth.PASSWORD);
        String staffEmail = "staff-" + UUID.randomUUID() + "@example.com";

        String created = mvc.perform(post("/api/v1/staff")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"fullName":"Ravi","email":"%s","password":"%s"}
                                """.formatted(staffEmail, TestAuth.PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("STAFF"))
                .andExpect(jsonPath("$.permissions").isArray())
                .andReturn().getResponse().getContentAsString();
        String staffId = TestAuth.jsonField(created, "id");

        String staffToken = TestAuth.login(mvc, staffEmail, TestAuth.PASSWORD);
        mvc.perform(get("/api/v1/staff").header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/staff").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(staffId));
    }

    @Test
    void staffCannotBeGrantedAPermissionTheActorLacks() throws Exception {
        Owner owner = seedOwner();
        String ownerToken = TestAuth.login(mvc, owner.email, TestAuth.PASSWORD);
        String limitedEmail = "lim-" + UUID.randomUUID() + "@example.com";
        String created = mvc.perform(post("/api/v1/staff")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"fullName":"Limited","email":"%s","password":"%s",
                                 "permissions":["staff.manage","customers.view"]}
                                """.formatted(limitedEmail, TestAuth.PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String limitedId = TestAuth.jsonField(created, "id");
        String limitedToken = TestAuth.login(mvc, limitedEmail, TestAuth.PASSWORD);

        String targetEmail = "tgt-" + UUID.randomUUID() + "@example.com";
        String target = mvc.perform(post("/api/v1/staff")
                        .header("Authorization", "Bearer " + limitedToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"fullName":"Target","email":"%s","password":"%s"}
                                """.formatted(targetEmail, TestAuth.PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String targetId = TestAuth.jsonField(target, "id");

        mvc.perform(put("/api/v1/staff/" + targetId + "/permissions")
                        .header("Authorization", "Bearer " + limitedToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"permissions\":[\"customers.view\",\"payments.record\"]}"))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/staff/" + limitedId).header("Authorization", "Bearer " + ownerToken))
                .andExpect(jsonPath("$.id").value(limitedId));
    }

    @Test
    void deleteDisablesStaffLogin() throws Exception {
        Owner owner = seedOwner();
        String ownerToken = TestAuth.login(mvc, owner.email, TestAuth.PASSWORD);
        String staffEmail = "gone-" + UUID.randomUUID() + "@example.com";
        String created = mvc.perform(post("/api/v1/staff")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"fullName":"Gone","email":"%s","password":"%s"}
                                """.formatted(staffEmail, TestAuth.PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String staffId = TestAuth.jsonField(created, "id");

        mvc.perform(delete("/api/v1/staff/" + staffId).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
                        .content(TestAuth.loginJson(staffEmail, TestAuth.PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ownerCannotSeeAnotherTenantsStaff() throws Exception {
        Owner a = seedOwner();
        Owner b = seedOwner();
        String tokenA = TestAuth.login(mvc, a.email, TestAuth.PASSWORD);
        String tokenB = TestAuth.login(mvc, b.email, TestAuth.PASSWORD);
        String created = mvc.perform(post("/api/v1/staff")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"fullName":"B Staff","email":"%s","password":"%s"}
                                """.formatted("b-" + UUID.randomUUID() + "@example.com", TestAuth.PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String staffB = TestAuth.jsonField(created, "id");

        mvc.perform(get("/api/v1/staff/" + staffB).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());
    }

    private Owner seedOwner() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String email = "owner-" + userId + "@example.com";
        var db = ownerJdbc();
        db.update("insert into tenants(id, name, slug, business_type, status) values (?, ?, ?, 'GYM', 'ACTIVE')",
                tenantId, "T", "t-" + tenantId);
        db.update("insert into tenant_settings(tenant_id) values (?)", tenantId);
        db.update("""
                insert into users(id, tenant_id, email, full_name, password_hash, role_code, status)
                values (?, ?, ?, 'Owner', ?, 'BUSINESS_OWNER', 'ACTIVE')
                """, userId, tenantId, email, encoder.encode(TestAuth.PASSWORD));
        return new Owner(userId, tenantId, email);
    }

    private record Owner(UUID id, UUID tenantId, String email) {}
}
