package com.example.reservation.auth.service;

import com.example.reservation.auth.domain.RefreshTokenEntity;
import java.util.UUID;

public interface RefreshTokenService {
    RefreshTokenEntity createRefreshToken(UUID userId);
    RefreshTokenEntity verifyAndRotate(String token);
    void revokeToken(String token);
    void revokeAllForUser(UUID userId);
}
