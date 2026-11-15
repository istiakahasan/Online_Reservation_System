package com.example.reservation.notification.repo;

import com.example.reservation.notification.domain.NotificationLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLogEntity, UUID> {
    List<NotificationLogEntity> findAllByReservationId(UUID reservationId);
    List<NotificationLogEntity> findAllByStatus(String status);
}
