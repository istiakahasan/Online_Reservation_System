package com.example.reservation.reservation.repo;

import com.example.reservation.reservation.domain.ReservationEntity;
import com.example.reservation.reservation.domain.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface ReservationRepository extends JpaRepository<ReservationEntity, UUID> {
    List<ReservationEntity> findAllByBusinessId(UUID businessId);
    List<ReservationEntity> findAllByCustomerId(UUID customerId);
    List<ReservationEntity> findAllByStaffIdAndStartTimeBetween(UUID staffId, Instant from, Instant to);
    List<ReservationEntity> findAllByStatusAndHoldExpiresAtBefore(ReservationStatus status, Instant now);
}
