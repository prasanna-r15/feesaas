package com.feesaas;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FeeSaasApplication {

    public static void main(String[] args) {
        ensureDatabaseExists();
        SpringApplication.run(FeeSaasApplication.class, args);
    }

    /** Creates the feesaas database if this is a blank Postgres (Flyway then creates tables). */
    static void ensureDatabaseExists() {
        String adminUrl = System.getenv().getOrDefault("DB_ADMIN_URL", "jdbc:postgresql://localhost:5433/postgres");
        String user = System.getenv().getOrDefault("DB_OWNER_USER", "postgres");
        String password = System.getenv().getOrDefault("DB_OWNER_PASSWORD", "welcome123");
        try {
            Class.forName("org.postgresql.Driver");
            try (Connection connection = DriverManager.getConnection(adminUrl, user, password);
                 Statement statement = connection.createStatement();
                 ResultSet existing = statement.executeQuery(
                         "select 1 from pg_database where datname = 'feesaas'")) {
                if (!existing.next()) {
                    statement.execute("create database feesaas");
                }
            }
        } catch (Exception e) {
            System.err.println("Could not ensure database feesaas exists: " + e.getMessage());
        }
    }
}
