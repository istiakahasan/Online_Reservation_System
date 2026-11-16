package com.example.reservation.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Authentication response with JWT token pair")
public record AuthResponseDto(
        @Schema(description = "JWT Access Token")
        String accessToken,

        @Schema(description = "Refresh Token")
        String refreshToken,

        @Schema(description = "Token type", example = "Bearer")
        String tokenType,

        @Schema(description = "Expires in seconds", example = "3600")
        long expiresIn,

        @Schema(description = "Authenticated user ID")
        UUID userId,

        @Schema(description = "Authenticated business ID")
        UUID businessId,

        @Schema(description = "Assigned user role", example = "CUSTOMER")
        String role
) {
    public static AuthResponseDto of(String accessToken, String refreshToken, long expiresIn, UUID userId, UUID businessId, String role) {
        return new AuthResponseDto(accessToken, refreshToken, "Bearer", expiresIn, userId, businessId, role);
    }
}
