package com.example.reservation.notification.service.channel;

import com.example.reservation.notification.domain.NotificationLogEntity;
import com.example.reservation.notification.repo.NotificationLogRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SmsChannel implements NotificationChannel {

    private final NotificationLogRepository notificationLogRepository;

    @Override
    public String getChannelType() {
        return "SMS";
    }

    @Override
    public void send(NotificationRequest request) {
        log.info("Simulated SMS dispatch to {}: subject='{}', template='{}'",
                request.recipient(), request.subject(), request.templateName());

        NotificationLogEntity logEntry = new NotificationLogEntity(
                request.reservationId(),
                getChannelType(),
                request.templateName(),
                request.recipient(),
                "SENT",
                null,
                Instant.now()
        );
        notificationLogRepository.saveAndFlush(logEntry);
    }
}
