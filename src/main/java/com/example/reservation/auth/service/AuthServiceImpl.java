package com.example.reservation.auth.service;

import com.example.reservation.auth.api.dto.AuthResponseDto;
import com.example.reservation.auth.api.dto.LoginRequestDto;
import com.example.reservation.auth.api.dto.RegisterRequestDto;
import com.example.reservation.auth.domain.RefreshTokenEntity;
import com.example.reservation.business.domain.BusinessEntity;
import com.example.reservation.business.repo.BusinessRepository;
import com.example.reservation.shared.exception.AppException;
import com.example.reservation.user.domain.UserEntity;
import com.example.reservation.user.domain.UserRole;
import com.example.reservation.user.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final RefreshTokenService refreshTokenService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public AuthResponseDto register(RegisterRequestDto request) {
        BusinessEntity business = businessRepository.findById(request.businessId())
                .orElseThrow(() -> new AppException("Business tenant not found", "TENANT_NOT_FOUND", HttpStatus.NOT_FOUND) {});

        if (userRepository.findByBusinessIdAndEmail(request.businessId(), request.email()).isPresent()) {
            throw new AppException("Email already registered for this business", "EMAIL_ALREADY_EXISTS", HttpStatus.CONFLICT) {};
        }

        String hashedPassword = passwordEncoder.encode(request.password());

        UserEntity user = new UserEntity(
                business,
                request.firstName(),
                request.lastName(),
                request.email(),
                hashedPassword,
                request.phone(),
                UserRole.CUSTOMER
        );
        userRepository.save(user);

        return issueTokens(user);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponseDto login(LoginRequestDto request) {
        UserEntity user = userRepository.findByBusinessIdAndEmail(request.businessId(), request.email())
                .orElseThrow(() -> new AppException("Invalid email or password", "BAD_CREDENTIALS", HttpStatus.UNAUTHORIZED) {});

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new AppException("Invalid email or password", "BAD_CREDENTIALS", HttpStatus.UNAUTHORIZED) {};
        }

        return issueTokens(user);
    }

    @Override
    @Transactional
    public AuthResponseDto refresh(String refreshToken) {
        RefreshTokenEntity newRefreshToken = refreshTokenService.verifyAndRotate(refreshToken);
        UserEntity user = userRepository.findById(newRefreshToken.getUserId())
                .orElseThrow(() -> new AppException("User not found", "USER_NOT_FOUND", HttpStatus.UNAUTHORIZED) {});

        String newAccessToken = jwtService.generateAccessToken(
                user.getId(),
                user.getBusiness().getId(),
                user.getEmail(),
                user.getRole()
        );

        return AuthResponseDto.of(
                newAccessToken,
                newRefreshToken.getToken(),
                jwtService.getAccessValiditySeconds(),
                user.getId(),
                user.getBusiness().getId(),
                user.getRole().name()
        );
    }

    @Override
    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.revokeToken(refreshToken);
    }

    private AuthResponseDto issueTokens(UserEntity user) {
        String accessToken = jwtService.generateAccessToken(
                user.getId(),
                user.getBusiness().getId(),
                user.getEmail(),
                user.getRole()
        );

        RefreshTokenEntity refreshToken = refreshTokenService.createRefreshToken(user.getId());

        return AuthResponseDto.of(
                accessToken,
                refreshToken.getToken(),
                jwtService.getAccessValiditySeconds(),
                user.getId(),
                user.getBusiness().getId(),
                user.getRole().name()
        );
    }
}
