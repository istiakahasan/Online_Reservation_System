package com.example.reservation.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "User session logout request")
public record LogoutRequestDto(
        @Schema(description = "Refresh token to revoke")
        @NotBlank(message = "refreshToken is required")
        String refreshToken
) {}
