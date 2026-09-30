package com.feesaas.support;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.test.web.servlet.MockMvc;

public final class TestAuth {
    public static final String PASSWORD = "Testpass1!";
    public static final String DEVICE = "device-1";

    private TestAuth() {}

    public static String login(MockMvc mvc, String identifier, String password) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
                        .content(loginJson(identifier, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return jsonField(body, "accessToken");
    }

    public static String loginJson(String identifier, String password) {
        return "{\"identifier\":\"%s\",\"password\":\"%s\",\"deviceId\":\"%s\"}"
                .formatted(identifier, password, DEVICE);
    }

    public static String jsonField(String body, String field) {
        String needle = "\"" + field + "\":\"";
        int start = body.indexOf(needle);
        if (start < 0) {
            throw new IllegalStateException("Missing " + field + " in " + body);
        }
        int from = start + needle.length();
        return body.substring(from, body.indexOf('"', from));
    }
}
