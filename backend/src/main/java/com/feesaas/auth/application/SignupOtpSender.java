package com.feesaas.auth.application;

import com.feesaas.config.application.PlatformConfigService;
import com.feesaas.notify.infra.PlatformMailSettingsRepository.SettingsRow;
import com.feesaas.shared.error.ApiException;
import com.feesaas.shared.error.ErrorCode;
import com.feesaas.shared.text.ConfigTemplates;
import jakarta.mail.internet.MimeMessage;
import java.util.Map;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
public class SignupOtpSender {

    private static final Logger log = LoggerFactory.getLogger(SignupOtpSender.class);
    private static final String DEFAULT_SUBJECT = "Your DueMate signup code";
    private static final String DEFAULT_BODY =
            "<p>Hi {{name}},</p><p>Your verification code is <b>{{otp}}</b>.</p><p>It expires in 10 minutes.</p>";

    private final PlatformConfigService configs;

    public SignupOtpSender(PlatformConfigService configs) {
        this.configs = configs;
    }

    public void sendEmail(SettingsRow cfg, String to, String fullName, String otp) {
        sendTemplated(
                cfg,
                to,
                fullName,
                otp,
                "SIGNUP_OTP_SUBJECT",
                DEFAULT_SUBJECT,
                "SIGNUP_OTP_BODY",
                DEFAULT_BODY,
                "Could not send the email OTP. Check SMTP settings.");
    }

    public void sendPasswordOtp(SettingsRow cfg, String to, String fullName, String otp) {
        sendTemplated(
                cfg,
                to,
                fullName,
                otp,
                "PASSWORD_OTP_SUBJECT",
                "Your DueMate password reset code",
                "PASSWORD_OTP_BODY",
                "<p>Hi {{name}},</p><p>Your password reset code is <b>{{otp}}</b>.</p><p>It expires in 10 minutes.</p>",
                "Could not send the reset code. Check SMTP settings.");
    }

    private void sendTemplated(
            SettingsRow cfg,
            String to,
            String fullName,
            String otp,
            String subjectKey,
            String defaultSubject,
            String bodyKey,
            String defaultBody,
            String failMessage) {
        if (cfg.smtpHost() == null || cfg.smtpHost().isBlank() || cfg.smtpFrom() == null || cfg.smtpFrom().isBlank()) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    "Email OTP needs SMTP. Set host and from-address under pending dues email.");
        }
        String name = fullName == null || fullName.isBlank() ? "there" : fullName.trim();
        Map<String, String> vars = Map.of("name", escape(name), "otp", otp);
        String subject = ConfigTemplates.apply(configs.resolveOr(subjectKey, "DEFAULT", defaultSubject), vars);
        String html = ConfigTemplates.apply(configs.resolveOr(bodyKey, "DEFAULT", defaultBody), vars);
        try {
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
            helper.setSubject(subject);
            helper.setText(html, true);
            mailer.send(message);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("OTP email failed: {}", e.getMessage());
            throw new ApiException(ErrorCode.VALIDATION_FAILED, failMessage);
        }
    }

    private static String escape(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
