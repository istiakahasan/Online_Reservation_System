package com.example.reservation.notification.service;

import com.example.reservation.notification.api.dto.NotificationResponseDto;
import com.example.reservation.notification.api.dto.SendNotificationRequestDto;
import com.example.reservation.notification.repo.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;

    @Override
    @Transactional
    public NotificationResponseDto sendNotification(SendNotificationRequestDto request) {
        return null;
    }

    @Override
    public List<NotificationResponseDto> getNotificationsByBusiness(UUID businessId) {
        return Collections.emptyList();
    }
}
