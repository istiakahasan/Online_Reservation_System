package com.example.reservation.user.service;

import com.example.reservation.user.api.dto.UpdateUserRequestDto;
import com.example.reservation.user.api.dto.UserResponseDto;
import com.example.reservation.user.repo.UserRepository;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Override
    public UserResponseDto getUserById(UUID userId) {
        // Scaffold stub - business logic to be implemented
        return null;
    }

    @Override
    public List<UserResponseDto> getUsersByBusiness(UUID businessId) {
        // Scaffold stub - business logic to be implemented
        return Collections.emptyList();
    }

    @Override
    @Transactional
    public UserResponseDto updateUser(UUID userId, UpdateUserRequestDto request) {
        // Scaffold stub - business logic to be implemented
        return null;
    }
}
