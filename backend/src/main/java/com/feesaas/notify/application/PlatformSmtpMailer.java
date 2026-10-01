package com.feesaas.notify.application;

import com.feesaas.notify.infra.PlatformMailSettingsRepository.SettingsRow;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
public class PlatformSmtpMailer {

    private static final Logger log = LoggerFactory.getLogger(PlatformSmtpMailer.class);

    public void sendHtml(SettingsRow cfg, String to, String subject, String html) throws Exception {
        if (cfg == null || cfg.smtpHost() == null || cfg.smtpHost().isBlank()
                || cfg.smtpFrom() == null || cfg.smtpFrom().isBlank()) {
            throw new IllegalStateException("SMTP host and from-address are not set.");
        }
        JavaMailSenderImpl mailer = new JavaMailSenderImpl();
        mailer.setHost(cfg.smtpHost());
        mailer.setPort(cfg.smtpPort() <= 0 ? 587 : cfg.smtpPort());
        mailer.setUsername(cfg.smtpUsername());
        mailer.setPassword(cfg.smtpPassword());
        Properties props = mailer.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.connectiontimeout", "8000");
        props.put("mail.smtp.timeout", "8000");
        MimeMessage message = mailer.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
        helper.setFrom(cfg.smtpFrom());
        helper.setTo(to);
        helper.setSubject(subject == null || subject.isBlank() ? "DueMate" : subject);
        helper.setText(html == null ? "" : html, true);
        mailer.send(message);
        log.info("Sent platform mail to {}", to);
    }
}
