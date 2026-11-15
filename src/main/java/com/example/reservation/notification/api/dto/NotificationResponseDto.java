package com.example.reservation.notification.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Notification status details")
public record NotificationResponseDto(
        UUID id,
        UUID businessId,
        String recipientEmail,
        String subject,
        String status,
        Instant createdAt
) {}
