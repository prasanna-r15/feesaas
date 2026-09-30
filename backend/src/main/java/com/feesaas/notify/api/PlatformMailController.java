package com.feesaas.notify.api;

import com.feesaas.notify.application.PendingDuesMailService;
import com.feesaas.notify.application.PendingDuesMailService.RunResult;
import com.feesaas.notify.application.PendingDuesMailService.SettingsView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/mail")
public class PlatformMailController {

    private final PendingDuesMailService mail;

    public PlatformMailController(PendingDuesMailService mail) {
        this.mail = mail;
    }

    @GetMapping
    public SettingsView get() {
        return mail.view();
    }

    @PutMapping
    public SettingsView save(@RequestBody SaveRequest request) {
        return mail.save(
                request.enabled(),
                request.cronExpr(),
                request.timezone(),
                request.smtpHost(),
                request.smtpPort() == null ? 587 : request.smtpPort(),
                request.smtpUsername(),
                request.smtpPassword(),
                request.smtpFrom());
    }

    @PostMapping("/run")
    public RunResult run() {
        return mail.runNow();
    }

    public record SaveRequest(
            boolean enabled,
            String cronExpr,
            String timezone,
            String smtpHost,
            Integer smtpPort,
            String smtpUsername,
            String smtpPassword,
            String smtpFrom
    ) {}
}
