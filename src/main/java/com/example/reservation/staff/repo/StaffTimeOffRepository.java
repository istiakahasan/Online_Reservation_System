package com.example.reservation.staff.repo;

import com.example.reservation.staff.domain.StaffTimeOffEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface StaffTimeOffRepository extends JpaRepository<StaffTimeOffEntity, UUID> {
    @Query("""
        SELECT t FROM StaffTimeOffEntity t
        WHERE t.staff.id = :staffId
          AND t.startTime < :windowEnd
          AND t.endTime > :windowStart
    """)
    List<StaffTimeOffEntity> findOverlappingTimeOff(UUID staffId, Instant windowStart, Instant windowEnd);
}
