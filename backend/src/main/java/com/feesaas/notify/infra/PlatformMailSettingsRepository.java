package com.feesaas.notify.infra;

import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PlatformMailSettingsRepository {

    private final JdbcClient jdbc;

    public PlatformMailSettingsRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public SettingsRow load() {
        return jdbc.sql("""
                select enabled, cron_expr, timezone, smtp_host, smtp_port, smtp_username, smtp_password,
                       smtp_from, last_run_at, last_result
                  from platform_mail_settings where id = 1
                """)
                .query((rs, i) -> new SettingsRow(
                        rs.getBoolean("enabled"),
                        rs.getString("cron_expr"),
                        rs.getString("timezone"),
                        rs.getString("smtp_host"),
                        rs.getInt("smtp_port"),
                        rs.getString("smtp_username"),
                        rs.getString("smtp_password"),
                        rs.getString("smtp_from"),
                        Optional.ofNullable(rs.getTimestamp("last_run_at")).map(t -> t.toInstant()).orElse(null),
                        rs.getString("last_result")))
                .single();
    }

    public void save(
            boolean enabled,
            String cronExpr,
            String timezone,
            String smtpHost,
            int smtpPort,
            String smtpUsername,
            String smtpPassword,
            String smtpFrom,
            boolean updatePassword) {
        if (updatePassword) {
            jdbc.sql("""
                    update platform_mail_settings
                       set enabled = :on, cron_expr = :cron, timezone = :tz, smtp_host = :host, smtp_port = :port,
                           smtp_username = :user, smtp_password = :pass, smtp_from = :from
                     where id = 1
                    """)
                    .param("on", enabled).param("cron", cronExpr).param("tz", timezone)
                    .param("host", smtpHost).param("port", smtpPort).param("user", smtpUsername)
                    .param("pass", smtpPassword).param("from", smtpFrom)
                    .update();
            return;
        }
        jdbc.sql("""
                update platform_mail_settings
                   set enabled = :on, cron_expr = :cron, timezone = :tz, smtp_host = :host, smtp_port = :port,
                       smtp_username = :user, smtp_from = :from
                 where id = 1
                """)
                .param("on", enabled).param("cron", cronExpr).param("tz", timezone)
                .param("host", smtpHost).param("port", smtpPort).param("user", smtpUsername)
                .param("from", smtpFrom)
                .update();
    }

    public void markRun(String result) {
        jdbc.sql("update platform_mail_settings set last_run_at = :at, last_result = :result where id = 1")
                .param("at", java.sql.Timestamp.from(Instant.now()))
                .param("result", result)
                .update();
    }

    public record SettingsRow(
            boolean enabled,
            String cronExpr,
            String timezone,
            String smtpHost,
            int smtpPort,
            String smtpUsername,
            String smtpPassword,
            String smtpFrom,
            Instant lastRunAt,
            String lastResult
    ) {}
}
