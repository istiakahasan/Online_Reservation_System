package com.example.reservation.notification.service.channel;

import com.example.reservation.notification.domain.NotificationLogEntity;
import com.example.reservation.notification.repo.NotificationLogRepository;
import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailChannel implements NotificationChannel {

    private final SpringTemplateEngine templateEngine;
    private final NotificationLogRepository notificationLogRepository;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Override
    public String getChannelType() {
        return "EMAIL";
    }

    @Override
    @Retryable(
            retryFor = { Exception.class },
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    public void send(NotificationRequest request) {
        log.info("Sending email notification: recipient={}, template={}", request.recipient(), request.templateName());

        try {
            // 1. Render HTML email from Thymeleaf template
            Context context = new Context();
            if (request.templateModel() != null) {
                context.setVariables(request.templateModel());
            }
            String htmlContent = templateEngine.process(request.templateName(), context);

            // 2. Dispatch via JavaMailSender if configured
            JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
            if (mailSender != null) {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setTo(request.recipient());
                helper.setSubject(request.subject());
                helper.setText(htmlContent, true);
                mailSender.send(message);
                log.info("Email delivered via SMTP to {}", request.recipient());
            } else {
                log.info("Simulated email delivery to {}: subject='{}' (SMTP not configured)",
                        request.recipient(), request.subject());
            }

            // 3. Record success in notification_log
            logDelivery(request, "SENT", null);
        } catch (Exception ex) {
            log.warn("Email delivery attempt failed for recipient {}: {}. Triggering retry.",
                    request.recipient(), ex.getMessage());
            throw new RuntimeException("Email delivery failed: " + ex.getMessage(), ex);
        }
    }

    @Recover
    public void recover(Exception e, NotificationRequest request) {
        log.error("All 3 retry attempts exhausted for email to {}. Recording FAILED in notification_log: {}",
                request.recipient(), e.getMessage());
        logDelivery(request, "FAILED", e.getMessage());
    }

    private void logDelivery(NotificationRequest request, String status, String errorMsg) {
        try {
            NotificationLogEntity logEntry = new NotificationLogEntity(
                    request.reservationId(),
                    getChannelType(),
                    request.templateName(),
                    request.recipient(),
                    status,
                    errorMsg,
                    Instant.now()
            );
            notificationLogRepository.saveAndFlush(logEntry);
        } catch (Exception ex) {
            log.error("Failed to save notification_log entry: {}", ex.getMessage(), ex);
        }
    }
}
