package com.example.reservation.notification.api;

import com.example.reservation.notification.api.dto.NotificationResponseDto;
import com.example.reservation.notification.api.dto.SendNotificationRequestDto;
import com.example.reservation.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Endpoints for email/SMS communication")
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping
    @Operation(summary = "Send notification", description = "Dispatches an email or SMS notification")
    @ApiResponse(responseCode = "201", description = "Notification queued")
    public ResponseEntity<NotificationResponseDto> sendNotification(@Valid @RequestBody SendNotificationRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(notificationService.sendNotification(request));
    }

    @GetMapping
    @Operation(summary = "List notifications", description = "Retrieves sent notifications for a business")
    public ResponseEntity<List<NotificationResponseDto>> getNotifications(@RequestParam UUID businessId) {
        return ResponseEntity.ok(notificationService.getNotificationsByBusiness(businessId));
    }
}
