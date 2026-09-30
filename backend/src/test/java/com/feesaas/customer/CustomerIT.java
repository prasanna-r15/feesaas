package com.feesaas.customer;

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

class CustomerIT extends AbstractPostgresIT {

    @Autowired MockMvc mvc;
    @Autowired PasswordEncoder encoder;

    @Test
    void ownerCrudAndStaffNeedsPermission() throws Exception {
        Owner owner = seedOwner();
        String ownerToken = TestAuth.login(mvc, owner.email, TestAuth.PASSWORD);

        String created = mvc.perform(post("/api/v1/customers")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"fullName":"Rahul Sharma","phone":"+919800000001","email":"rahul@example.com","dueDate":"2026-10-15"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fullName").value("Rahul Sharma"))
                .andExpect(jsonPath("$.customerCode").value("C0001"))
                .andExpect(jsonPath("$.dueDate").value("2026-10-15"))
                .andReturn().getResponse().getContentAsString();
        String id = TestAuth.jsonField(created, "id");

        mvc.perform(get("/api/v1/customers").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].dueDate").value("2026-10-15"));

        mvc.perform(post("/api/v1/customers")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"fullName":"No Due","phone":"+919800000088"}
                                """))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/v1/customers").param("q", "Rahul").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].phone").value("+919800000001"));

        mvc.perform(patch("/api/v1/customers/" + id)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\",\"notes\":\"Paused\",\"dueDate\":\"2026-11-01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"))
                .andExpect(jsonPath("$.notes").value("Paused"))
                .andExpect(jsonPath("$.dueDate").value("2026-11-01"));

        mvc.perform(post("/api/v1/customers")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"fullName":"Copy","phone":"+919800000001","dueDate":"2026-12-01"}
                                """))
                .andExpect(status().isConflict());

        mvc.perform(delete("/api/v1/customers/" + id).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/customers/" + id).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void tenantACannotSeeTenantBCustomer() throws Exception {
        Owner a = seedOwner();
        Owner b = seedOwner();
        String tokenA = TestAuth.login(mvc, a.email, TestAuth.PASSWORD);
        String tokenB = TestAuth.login(mvc, b.email, TestAuth.PASSWORD);
        String created = mvc.perform(post("/api/v1/customers")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"fullName":"Secret","phone":"+919800000099","dueDate":"2026-10-01"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = TestAuth.jsonField(created, "id");

        mvc.perform(get("/api/v1/customers/" + id).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/customers").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
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
