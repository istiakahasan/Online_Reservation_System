package com.example.reservation.availability.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.example.reservation.availability.api.dto.TimeSlotResponseDto;
import com.example.reservation.business.domain.BusinessEntity;
import com.example.reservation.business.repo.BusinessRepository;
import com.example.reservation.reservation.repo.ReservationRepository;
import com.example.reservation.service.domain.ServiceEntity;
import com.example.reservation.service.repo.ServiceRepository;
import com.example.reservation.shared.observability.ReservationMetrics;
import com.example.reservation.staff.domain.StaffWorkingHoursEntity;
import com.example.reservation.staff.repo.StaffTimeOffRepository;
import com.example.reservation.staff.repo.StaffWorkingHoursRepository;
import com.example.reservation.user.domain.UserEntity;
import com.example.reservation.user.domain.UserRole;
import java.math.BigDecimal;
import java.time.*;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AvailabilityServiceImplTest {

        @Mock
        private BusinessRepository businessRepository;

        @Mock
        private ServiceRepository serviceRepository;

        @Mock
        private StaffWorkingHoursRepository workingHoursRepository;

        @Mock
        private StaffTimeOffRepository timeOffRepository;

        @Mock
        private ReservationRepository reservationRepository;

        @Mock
        private ReservationMetrics reservationMetrics;

        @InjectMocks
        private AvailabilityServiceImpl availabilityService;

        private final UUID businessId = UUID.randomUUID();
        private final UUID serviceId = UUID.randomUUID();
        private final UUID staffId = UUID.randomUUID();
        private final LocalDate targetDate = LocalDate.now().plusDays(3); // In future to ensure not past

        @Test
        @DisplayName("AvailabilityService coordinates repositories and returns computed slots")
        void shouldCoordinateRepositoriesAndReturnSlots() {
                // Arrange Business
                BusinessEntity business = new BusinessEntity("Bistro", "bistro", "Europe/Paris", "b@example.com",
                                "+123");
                when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));

                // Arrange Service: 45m duration + 15m buffer
                ServiceEntity service = new ServiceEntity(business, "Haircut", "Desc", 45, 15, new BigDecimal("50.00"),
                                "EUR");
                when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(service));

                // Arrange Staff & Working Hours (9:00 - 11:00 on this day)
                UserEntity staffUser = new UserEntity(business, "Jean", "Dupont", "jean@example.com", "hash", "+123",
                                UserRole.STAFF);
                StaffWorkingHoursEntity hours = new StaffWorkingHoursEntity(business, staffUser,
                                targetDate.getDayOfWeek(), LocalTime.of(9, 0), LocalTime.of(11, 0));
                when(workingHoursRepository.findAllByStaffIdAndDayOfWeek(staffId, targetDate.getDayOfWeek()))
                                .thenReturn(List.of(hours));

                // No time-offs
                when(timeOffRepository.findOverlappingTimeOff(eq(staffId), any(Instant.class), any(Instant.class)))
                                .thenReturn(Collections.emptyList());

                // No reservations
                when(reservationRepository.findAllByStaffIdAndStartTimeBetween(eq(staffId), any(Instant.class),
                                any(Instant.class)))
                                .thenReturn(Collections.emptyList());

                // Mock metrics recording execution
                when(reservationMetrics.recordAvailabilityLatency(any(), any()))
                                .thenAnswer(invocation -> ((java.util.function.Supplier<?>) invocation.getArgument(1))
                                                .get());

                // Act
                List<TimeSlotResponseDto> slots = availabilityService.getAvailableSlots(businessId, serviceId, staffId,
                                targetDate);

                // Assert: 9:00 to 11:00 (120m). 45m + 15m buffer = 60m per slot. Fits exactly 2
                // slots: 09:00 and 10:00.
                assertThat(slots).hasSize(2);
                assertThat(slots.get(0).durationMinutes()).isEqualTo(45);
                assertThat(slots.get(0).localStartTime().toLocalTime()).isEqualTo(LocalTime.of(9, 0));
                assertThat(slots.get(1).localStartTime().toLocalTime()).isEqualTo(LocalTime.of(10, 0));
        }
}
