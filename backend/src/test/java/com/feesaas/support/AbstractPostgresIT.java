package com.feesaas.support;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public abstract class AbstractPostgresIT {

    @Container
    protected static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("feesaas")
            .withUsername("feesaas_owner")
            .withPassword("feesaas_owner")
            .withInitScript("db/init.sql");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", PG::getJdbcUrl);
        r.add("spring.datasource.username", () -> "feesaas_app");
        r.add("spring.datasource.password", () -> "feesaas_app");
        r.add("spring.flyway.url", PG::getJdbcUrl);
        r.add("spring.flyway.user", () -> "feesaas_owner");
        r.add("spring.flyway.password", () -> "feesaas_owner");
        r.add("feesaas.auth.expose-reset-token", () -> "true");
        r.add("feesaas.auth.expose-otp", () -> "true");
        r.add("feesaas.seed-demo", () -> "false");
    }

    protected JdbcTemplate ownerJdbc() {
        return new JdbcTemplate(new DriverManagerDataSource(PG.getJdbcUrl(), "feesaas_owner", "feesaas_owner"));
    }
}
