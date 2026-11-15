package com.example.reservation.auth.service;

import com.example.reservation.auth.api.dto.AuthResponseDto;
import com.example.reservation.auth.api.dto.LoginRequestDto;
import com.example.reservation.auth.api.dto.RegisterRequestDto;

public interface AuthService {
    AuthResponseDto register(RegisterRequestDto request);
    AuthResponseDto login(LoginRequestDto request);
    AuthResponseDto refresh(String refreshToken);
    void logout(String refreshToken);
}
