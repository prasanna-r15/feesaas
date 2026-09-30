package com.feesaas.auth;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.feesaas.support.AbstractPostgresIT;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class AuthIT extends AbstractPostgresIT {

    static final String PASSWORD = "Testpass1!";
    static final String DEVICE = "device-1";

    @Autowired MockMvc mvc;
    @Autowired PasswordEncoder encoder;

    @Test
    void registerCreatesPersonalWorkspace() throws Exception {
        String email = "person-" + UUID.randomUUID() + "@example.com";
        String phone = "+91" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        MvcResult started = mvc.perform(post("/api/v1/auth/register").contentType(APPLICATION_JSON)
                        .content("{\"fullName\":\"Prasanna\",\"email\":\"%s\",\"phone\":\"%s\",\"password\":\"%s\",\"deviceId\":\"%s\"}"
                                .formatted(email, phone, PASSWORD, DEVICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.challengeId").isNotEmpty())
                .andExpect(jsonPath("$.channel").value("EMAIL"))
                .andExpect(jsonPath("$.otp").isNotEmpty())
                .andReturn();
        String otp = json(started.getResponse().getContentAsString(), "otp");
        String challengeId = json(started.getResponse().getContentAsString(), "challengeId");
        mvc.perform(post("/api/v1/auth/register/verify").contentType(APPLICATION_JSON)
                        .content("{\"challengeId\":\"%s\",\"otp\":\"%s\",\"deviceId\":\"%s\"}"
                                .formatted(challengeId, otp, DEVICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("INDIVIDUAL"));
        String access = json(login(email, PASSWORD), "accessToken");
        mvc.perform(get("/api/v1/me/bootstrap").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeContext.kind").value("PERSONAL"))
                .andExpect(jsonPath("$.needsOnboarding").value(true));
        mvc.perform(get("/api/v1/personal/summary").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expenseMinor").value(0));
    }

    @Test
    void loginWithEmailThenMe() throws Exception {
        SeededUser user = seedOwner("ACTIVE");
        String body = login(user.email, PASSWORD);
        String access = json(body, "accessToken");
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.id.toString()))
                .andExpect(jsonPath("$.role").value("BUSINESS_OWNER"));
    }

    @Test
    void loginWithPhone() throws Exception {
        SeededUser user = seedOwner("ACTIVE");
        mvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON).content(loginJson(user.phone, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(user.id.toString()));
    }

    @Test
    void badPasswordIsGeneric401() throws Exception {
        SeededUser user = seedOwner("ACTIVE");
        mvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON).content(loginJson(user.email, "wrong-pass")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void lockoutAfterTooManyFailures() throws Exception {
        SeededUser user = seedOwner("ACTIVE");
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON).content(loginJson(user.email, "wrong-pass")))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON).content(loginJson(user.email, PASSWORD)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void suspendedTenantCannotLogin() throws Exception {
        SeededUser user = seedOwner("SUSPENDED");
        mvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON).content(loginJson(user.email, PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_SUSPENDED"));
    }

    @Test
    void refreshRotatesAndReuseRevokesFamily() throws Exception {
        SeededUser user = seedOwner("ACTIVE");
        String first = login(user.email, PASSWORD);
        String refresh1 = json(first, "refreshToken");

        String second = mvc.perform(post("/api/v1/auth/refresh").contentType(APPLICATION_JSON)
                        .content(refreshJson(refresh1)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String refresh2 = json(second, "refreshToken");

        mvc.perform(post("/api/v1/auth/refresh").contentType(APPLICATION_JSON).content(refreshJson(refresh1)))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/v1/auth/refresh").contentType(APPLICATION_JSON).content(refreshJson(refresh2)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesRefresh() throws Exception {
        SeededUser user = seedOwner("ACTIVE");
        String session = login(user.email, PASSWORD);
        String refresh = json(session, "refreshToken");
        mvc.perform(post("/api/v1/auth/logout").contentType(APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(refresh)))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/refresh").contentType(APPLICATION_JSON).content(refreshJson(refresh)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePasswordInvalidatesAccessAndRefresh() throws Exception {
        SeededUser user = seedOwner("ACTIVE");
        String session = login(user.email, PASSWORD);
        String access = json(session, "accessToken");
        String refresh = json(session, "refreshToken");

        mvc.perform(post("/api/v1/auth/password/change")
                        .header("Authorization", "Bearer " + access)
                        .contentType(APPLICATION_JSON)
                        .content("{\"currentPassword\":\"%s\",\"newPassword\":\"Newpass1!\"}".formatted(PASSWORD)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").contentType(APPLICATION_JSON).content(refreshJson(refresh)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON).content(loginJson(user.email, "Newpass1!")))
                .andExpect(status().isOk());
    }

    @Test
    void forgotAndResetPassword() throws Exception {
        SeededUser user = seedOwner("ACTIVE");
        MvcResult forgot = mvc.perform(post("/api/v1/auth/password/forgot").contentType(APPLICATION_JSON)
                        .content("{\"identifier\":\"%s\"}".formatted(user.email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resetToken").isNotEmpty())
                .andReturn();
        String resetToken = json(forgot.getResponse().getContentAsString(), "resetToken");

        mvc.perform(post("/api/v1/auth/password/reset").contentType(APPLICATION_JSON)
                        .content("{\"resetToken\":\"%s\",\"newPassword\":\"Resetpass1!\"}".formatted(resetToken)))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON).content(loginJson(user.email, "Resetpass1!")))
                .andExpect(status().isOk());
    }

    @Test
    void unauthenticatedMeIs401() throws Exception {
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    }

    private String login(String identifier, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON).content(loginJson(identifier, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
    }

    private SeededUser seedOwner(String tenantStatus) {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String email = "owner-" + userId + "@example.com";
        String phone = "+91" + userId.toString().replace("-", "").substring(0, 10);
        JdbcTemplate owner = ownerJdbc();
        owner.update("""
                insert into tenants(id, name, slug, business_type, status)
                values (?, ?, ?, 'GYM', ?)
                """, tenantId, "Gym " + tenantId, "g-" + tenantId, tenantStatus);
        owner.update("insert into tenant_settings(tenant_id) values (?)", tenantId);
        owner.update("""
                insert into users(id, tenant_id, email, phone, full_name, password_hash, role_code, status)
                values (?, ?, ?, ?, 'Owner', ?, 'BUSINESS_OWNER', 'ACTIVE')
                """, userId, tenantId, email, phone, encoder.encode(PASSWORD));
        return new SeededUser(userId, tenantId, email, phone);
    }

    private static String loginJson(String identifier, String password) {
        return "{\"identifier\":\"%s\",\"password\":\"%s\",\"deviceId\":\"%s\"}"
                .formatted(identifier, password, DEVICE);
    }

    private static String refreshJson(String refreshToken) {
        return "{\"refreshToken\":\"%s\",\"deviceId\":\"%s\"}".formatted(refreshToken, DEVICE);
    }

    private static String json(String body, String field) {
        String needle = "\"" + field + "\":\"";
        int start = body.indexOf(needle);
        if (start < 0) {
            throw new IllegalStateException("Missing " + field + " in " + body);
        }
        int from = start + needle.length();
        int to = body.indexOf('"', from);
        return body.substring(from, to);
    }

    private record SeededUser(UUID id, UUID tenantId, String email, String phone) {}
}
