package com.feesaas.configuration.infra;

import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PresetRepository {

    private final JdbcClient jdbc;

    public PresetRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Preset> findByCode(String code) {
        return jdbc.sql("select code, name, labels::text as labels, modules::text as modules from business_type_presets where code = :code")
                .param("code", code)
                .query((rs, i) -> new Preset(
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("labels"),
                        rs.getString("modules")))
                .optional();
    }

    public record Preset(String code, String name, String labelsJson, String modulesJson) {}
}
