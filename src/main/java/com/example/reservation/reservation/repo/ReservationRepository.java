package com.example.reservation.reservation.repo;

import com.example.reservation.reservation.domain.ReservationEntity;
import com.example.reservation.reservation.domain.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    long countByStatusIn(List<ReservationStatus> statuses);

    @Query("SELECT r FROM ReservationEntity r WHERE r.status = :status AND (r.holdExpiresAt <= :now OR r.createdAt <= :threshold)")
    List<ReservationEntity> findPendingForExpiration(
            @Param("status") ReservationStatus status,
            @Param("now") Instant now,
            @Param("threshold") Instant threshold
    );

    @Query("SELECT r FROM ReservationEntity r WHERE r.status = :status AND r.reminderSentAt IS NULL AND r.startTime BETWEEN :windowStart AND :windowEnd")
    List<ReservationEntity> findConfirmedForReminder(
            @Param("status") ReservationStatus status,
            @Param("windowStart") Instant windowStart,
            @Param("windowEnd") Instant windowEnd
    );

    @Query("SELECT r FROM ReservationEntity r WHERE r.status = :status AND r.startTime <= :cutoffTime")
    List<ReservationEntity> findConfirmedPastStartTime(
            @Param("status") ReservationStatus status,
            @Param("cutoffTime") Instant cutoffTime
    );
}
