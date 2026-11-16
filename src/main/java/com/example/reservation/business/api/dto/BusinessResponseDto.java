package com.example.reservation.business.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Business tenant response")
public record BusinessResponseDto(
        @Schema(description = "Business unique ID")
        UUID id,

        @Schema(description = "Business name", example = "The Parisian Bistro")
        String name,

        @Schema(description = "Vanity URL slug", example = "parisian-bistro")
        String slug,

        @Schema(description = "Business IANA timezone", example = "America/New_York")
        String timezone,

        @Schema(description = "Business email", example = "contact@parisianbistro.com")
        String email,

        @Schema(description = "Business phone", example = "+12125550199")
        String phone,

        @Schema(description = "Business status", example = "ACTIVE")
        String status,

        @Schema(description = "Creation timestamp")
        Instant createdAt
) {}
