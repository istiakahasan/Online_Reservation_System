package com.example.reservation.auth.service;

import com.example.reservation.auth.domain.RefreshTokenEntity;
import com.example.reservation.auth.repo.AuthRepository;
import com.example.reservation.shared.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final AuthRepository authRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${application.jwt.refresh-expiration-days:7}")
    private long refreshValidityDays;

    @Override
    @Transactional
    public RefreshTokenEntity createRefreshToken(UUID userId) {
        String token = generateRandomTokenString();
        Instant expiresAt = Instant.now().plus(refreshValidityDays, ChronoUnit.DAYS);

        RefreshTokenEntity entity = new RefreshTokenEntity(userId, token, expiresAt);
        return authRepository.save(entity);
    }

    @Override
    @Transactional
    public RefreshTokenEntity verifyAndRotate(String tokenString) {
        RefreshTokenEntity existing = authRepository.findByToken(tokenString)
                .orElseThrow(() -> new AppException("Invalid refresh token", "INVALID_REFRESH_TOKEN", HttpStatus.UNAUTHORIZED) {});

        if (existing.isRevoked() || existing.getExpiresAt().isBefore(Instant.now())) {
            existing.setRevoked(true);
            authRepository.save(existing);
            throw new AppException("Refresh token is expired or revoked", "EXPIRED_REFRESH_TOKEN", HttpStatus.UNAUTHORIZED) {};
        }

        // Revoke the old token (Rotation)
        existing.setRevoked(true);
        authRepository.save(existing);

        // Issue a brand new token
        return createRefreshToken(existing.getUserId());
    }

    @Override
    @Transactional
    public void revokeToken(String tokenString) {
        authRepository.findByToken(tokenString).ifPresent(entity -> {
            entity.setRevoked(true);
            authRepository.save(entity);
        });
    }

    @Override
    @Transactional
    public void revokeAllForUser(UUID userId) {
        authRepository.deleteByUserId(userId);
    }

    private String generateRandomTokenString() {
        byte[] bytes = new byte[64];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
