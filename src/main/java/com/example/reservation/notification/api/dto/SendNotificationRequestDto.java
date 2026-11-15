package com.example.reservation.notification.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Dispatch notification request")
public record SendNotificationRequestDto(
        @NotNull(message = "businessId is required")
        UUID businessId,

        @NotBlank(message = "recipientEmail is required")
        @Email(message = "Must be a valid email")
        String recipientEmail,

        @NotBlank(message = "subject is required")
        String subject,

        @NotBlank(message = "content is required")
        String content
) {}
