package com.example.reservation.business.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "Create new business tenant request")
public record CreateBusinessRequestDto(
        @Schema(description = "Business legal/display name", example = "The Parisian Bistro")
        @NotBlank(message = "Business name is required")
        String name,

        @Schema(description = "Unique vanity URL slug", example = "parisian-bistro")
        @NotBlank(message = "Slug is required")
        @Pattern(regexp = "^[a-z0-9-]+$", message = "Slug must contain only lowercase alphanumeric characters and hyphens")
        String slug,

        @Schema(description = "IANA timezone ID", example = "America/New_York")
        @NotBlank(message = "Timezone is required")
        String timezone,

        @Schema(description = "Contact email", example = "contact@parisianbistro.com")
        @NotBlank(message = "Email is required")
        @Email(message = "Must be a valid email address")
        String email,

        @Schema(description = "Contact phone number", example = "+12125550199")
        String phone
) {}

