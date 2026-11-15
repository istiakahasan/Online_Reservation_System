package com.example.reservation.notification.service;

import com.example.reservation.notification.api.dto.NotificationResponseDto;
import com.example.reservation.notification.api.dto.SendNotificationRequestDto;

import java.util.List;
import java.util.UUID;

public interface NotificationService {
    NotificationResponseDto sendNotification(SendNotificationRequestDto request);
    List<NotificationResponseDto> getNotificationsByBusiness(UUID businessId);
}
