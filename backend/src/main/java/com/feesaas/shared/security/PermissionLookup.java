package com.feesaas.shared.security;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class PermissionLookup {

    private final JdbcClient jdbc;

    public PermissionLookup(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Set<String> forUser(UUID userId) {
        return new HashSet<>(jdbc.sql("select permission_code from auth_permissions(:id)")
                .param("id", userId)
                .query(String.class)
                .list());
    }

    public boolean has(UUID userId, String permission) {
        return forUser(userId).contains(permission);
    }
}
