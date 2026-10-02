package com.feesaas.health.application;

import com.feesaas.config.application.PlatformConfigService;
import com.feesaas.shared.tenancy.TenantExecutor;
import com.feesaas.shared.tenancy.TenantScope;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

@Component
public class BackendKeepAliveScheduler implements SchedulingConfigurer {

    static final String INTERVAL_KEY = "BACKEND_KEEP_ALIVE_INTERVAL_MINUTES";
    static final String ENABLED_KEY = "BACKEND_KEEP_ALIVE_ENABLED";
    static final int DEFAULT_MINUTES = 10;
    static final int MAX_MINUTES = 1440;

    private static final Logger log = LoggerFactory.getLogger(BackendKeepAliveScheduler.class);

    private final PlatformConfigService configs;
    private final TenantExecutor executor;
    private final Environment env;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    public BackendKeepAliveScheduler(
            PlatformConfigService configs,
            TenantExecutor executor,
            Environment env) {
        this.configs = configs;
        this.executor = executor;
        this.env = env;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addTriggerTask(this::pingSafely, context -> {
            if (context.lastCompletion() == null) {
                return Instant.now().plusSeconds(15);
            }
            return Instant.now().plus(nextDelay());
        });
    }

    void pingSafely() {
        Settings settings = readSettings();
        if (!settings.enabled() || settings.minutes() == null) {
            return;
        }
        try {
            int port = listenPort();
            if (port <= 0) {
                log.debug("Keep-alive skipped; server port is not ready");
                return;
            }
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/health/keep-alive"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() / 100 != 2) {
                log.warn("Keep-alive returned HTTP {}", response.statusCode());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("Keep-alive request failed: {}", e.getMessage());
        }
    }

    Duration nextDelay() {
        Settings settings = readSettings();
        if (!settings.enabled()) {
            return Duration.ofSeconds(60);
        }
        if (settings.minutes() == null) {
            log.warn("Keep-alive interval is invalid; waiting 60s before the next check");
            return Duration.ofSeconds(60);
        }
        return Duration.ofMinutes(settings.minutes());
    }

    Settings readSettings() {
        try {
            return executor.call(TenantScope.platformAdmin(), () -> new Settings(
                    parseEnabled(configs.resolveOr(ENABLED_KEY, "DEFAULT", "true")),
                    parseMinutes(configs.resolveOr(INTERVAL_KEY, "DEFAULT", String.valueOf(DEFAULT_MINUTES)))));
        } catch (RuntimeException e) {
            log.warn("Could not read keep-alive config: {}", e.getMessage());
            return new Settings(true, DEFAULT_MINUTES);
        }
    }

    int listenPort() {
        String local = env.getProperty("local.server.port");
        if (local != null && !local.isBlank()) {
            try {
                return Integer.parseInt(local.trim());
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return env.getProperty("server.port", Integer.class, 9085);
    }

    static Boolean parseEnabled(String raw) {
        if (raw == null || raw.isBlank()) {
            return true;
        }
        String value = raw.trim().toLowerCase();
        if (value.equals("true") || value.equals("1") || value.equals("yes") || value.equals("on")) {
            return true;
        }
        if (value.equals("false") || value.equals("0") || value.equals("no") || value.equals("off")) {
            return false;
        }
        return true;
    }

    static Integer parseMinutes(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_MINUTES;
        }
        try {
            int minutes = Integer.parseInt(raw.trim());
            if (minutes < 1 || minutes > MAX_MINUTES) {
                return null;
            }
            return minutes;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    record Settings(boolean enabled, Integer minutes) {}
}
