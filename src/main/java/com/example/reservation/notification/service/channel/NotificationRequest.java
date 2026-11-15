package com.example.reservation.notification.service.channel;

import java.util.Map;
import java.util.UUID;

public record NotificationRequest(
        UUID reservationId,
        String recipient,
        String subject,
        String templateName,
        Map<String, Object> templateModel
) {}
