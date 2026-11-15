package com.example.reservation.auth.service;

import com.example.reservation.auth.api.dto.AuthResponseDto;
import com.example.reservation.auth.api.dto.LoginRequestDto;
import com.example.reservation.auth.api.dto.RegisterRequestDto;
import com.example.reservation.auth.repo.AuthRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthServiceImpl implements AuthService {

    private final AuthRepository authRepository;

    @Override
    @Transactional
    public AuthResponseDto login(LoginRequestDto request) {
        // Scaffold stub - business logic to be implemented
        return null;
    }

    @Override
    @Transactional
    public AuthResponseDto register(RegisterRequestDto request) {
        // Scaffold stub - business logic to be implemented
        return null;
    }

    @Override
    @Transactional
    public AuthResponseDto refreshToken(String refreshToken) {
        // Scaffold stub - business logic to be implemented
        return null;
    }
}

