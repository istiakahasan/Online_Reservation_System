package com.example.reservation.user.service;

import com.example.reservation.user.api.dto.UpdateUserRequestDto;
import com.example.reservation.user.api.dto.UserResponseDto;

import java.util.List;
import java.util.UUID;

public interface UserService {
    UserResponseDto getUserById(UUID userId);
    List<UserResponseDto> getUsersByBusiness(UUID businessId);
    UserResponseDto updateUser(UUID userId, UpdateUserRequestDto request);
}
