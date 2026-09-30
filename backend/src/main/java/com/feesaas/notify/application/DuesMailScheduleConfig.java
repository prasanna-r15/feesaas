package com.feesaas.notify.application;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;

@Configuration
public class DuesMailScheduleConfig implements SchedulingConfigurer {

    private final PendingDuesMailService mail;
    private final com.feesaas.notify.infra.PlatformMailSettingsRepository settings;
    private final com.feesaas.shared.tenancy.TenantExecutor executor;

    public DuesMailScheduleConfig(
            PendingDuesMailService mail,
            com.feesaas.notify.infra.PlatformMailSettingsRepository settings,
            com.feesaas.shared.tenancy.TenantExecutor executor) {
        this.mail = mail;
        this.settings = settings;
        this.executor = executor;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addTriggerTask(mail::runScheduled, context -> {
            var row = executor.call(com.feesaas.shared.tenancy.TenantScope.platformAdmin(), settings::load);
            if (!row.enabled() || row.cronExpr() == null || row.cronExpr().isBlank()) {
                return java.time.Instant.now().plusSeconds(300);
            }
            try {
                java.util.TimeZone zone = java.util.TimeZone.getTimeZone(
                        row.timezone() == null ? "Asia/Kolkata" : row.timezone());
                return new CronTrigger(row.cronExpr(), zone).nextExecution(context);
            } catch (RuntimeException e) {
                return java.time.Instant.now().plusSeconds(3600);
            }
        });
    }
}
