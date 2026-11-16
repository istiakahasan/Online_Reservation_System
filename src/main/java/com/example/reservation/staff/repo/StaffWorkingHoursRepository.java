package com.example.reservation.staff.repo;

import com.example.reservation.staff.domain.StaffWorkingHoursEntity;
import java.time.DayOfWeek;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StaffWorkingHoursRepository extends JpaRepository<StaffWorkingHoursEntity, UUID> {
    List<StaffWorkingHoursEntity> findAllByStaffIdAndDayOfWeek(UUID staffId, DayOfWeek dayOfWeek);
    List<StaffWorkingHoursEntity> findAllByStaffId(UUID staffId);
}
