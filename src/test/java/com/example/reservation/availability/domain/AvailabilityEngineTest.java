package com.example.reservation.availability.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.*;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AvailabilityEngineTest {

    private final UUID businessId = UUID.randomUUID();
    private final UUID staffId = UUID.randomUUID();
    private final ZoneId berlinZone = ZoneId.of("Europe/Berlin");
    private final LocalDate normalDate = LocalDate.of(2026, 6, 15); // Monday (DayOfWeek.MONDAY)

    @Test
    @DisplayName("1. Fully booked day returns empty list")
    void fullyBookedDayReturnsEmpty() {
        WorkingHoursWindow hours = new WorkingHoursWindow(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0));
        ZonedDateTime start = ZonedDateTime.of(normalDate, LocalTime.of(9, 0), berlinZone);
        ZonedDateTime end = ZonedDateTime.of(normalDate, LocalTime.of(12, 0), berlinZone);

        // Entire shift booked
        TimeInterval fullBooking = new TimeInterval(start.toInstant(), end.toInstant());

        AvailabilityCalculationRequest request = new AvailabilityCalculationRequest(
                businessId,
                staffId,
                normalDate,
                berlinZone,
                Duration.ofMinutes(60),
                Duration.ZERO,
                Duration.ofMinutes(60),
                List.of(hours),
                Collections.emptyList(),
                List.of(fullBooking),
                Instant.MIN
        );

        List<AvailableSlot> slots = AvailabilityEngine.computeAvailableSlots(request);
        assertThat(slots).isEmpty();
    }

    @Test
    @DisplayName("2. Partial availability returns only unbooked slots")
    void partialAvailabilityReturnsOpenSlots() {
        // Shift: 09:00 - 12:00 (3 hours). 10:00 - 11:00 is booked.
        WorkingHoursWindow hours = new WorkingHoursWindow(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0));
        ZonedDateTime bookedStart = ZonedDateTime.of(normalDate, LocalTime.of(10, 0), berlinZone);
        ZonedDateTime bookedEnd = ZonedDateTime.of(normalDate, LocalTime.of(11, 0), berlinZone);

        TimeInterval booking = new TimeInterval(bookedStart.toInstant(), bookedEnd.toInstant());

        AvailabilityCalculationRequest request = new AvailabilityCalculationRequest(
                businessId,
                staffId,
                normalDate,
                berlinZone,
                Duration.ofMinutes(60),
                Duration.ZERO,
                Duration.ofMinutes(60),
                List.of(hours),
                Collections.emptyList(),
                List.of(booking),
                Instant.MIN
        );

        List<AvailableSlot> slots = AvailabilityEngine.computeAvailableSlots(request);

        assertThat(slots).hasSize(2);
        assertThat(slots.get(0).localStartTime().toLocalTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(slots.get(1).localStartTime().toLocalTime()).isEqualTo(LocalTime.of(11, 0));
    }

    @Test
    @DisplayName("3. Overlapping time-off blocks slots correctly")
    void overlappingTimeOffBlocksSlots() {
        // Shift: 09:00 - 13:00. Time off: 10:30 - 12:30.
        WorkingHoursWindow hours = new WorkingHoursWindow(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(13, 0));
        ZonedDateTime toStart = ZonedDateTime.of(normalDate, LocalTime.of(10, 30), berlinZone);
        ZonedDateTime toEnd = ZonedDateTime.of(normalDate, LocalTime.of(12, 30), berlinZone);

        TimeInterval timeOff = new TimeInterval(toStart.toInstant(), toEnd.toInstant());

        AvailabilityCalculationRequest request = new AvailabilityCalculationRequest(
                businessId,
                staffId,
                normalDate,
                berlinZone,
                Duration.ofMinutes(60),
                Duration.ZERO,
                Duration.ofMinutes(60),
                List.of(hours),
                List.of(timeOff),
                Collections.emptyList(),
                Instant.MIN
        );

        List<AvailableSlot> slots = AvailabilityEngine.computeAvailableSlots(request);

        // 09:00 - 10:00 (fits)
        // 10:00 - 11:00 (overlaps with time-off starting 10:30 -> excluded)
        // 11:00 - 12:00 (overlaps with time-off -> excluded)
        // 12:00 - 13:00 (overlaps with time-off ending 12:30 -> excluded)
        assertThat(slots).hasSize(1);
        assertThat(slots.get(0).localStartTime().toLocalTime()).isEqualTo(LocalTime.of(9, 0));
    }

    @Test
    @DisplayName("4. DST day (Europe/Berlin spring forward 2026-03-29) skips non-existent hour")
    void dstSpringForwardHandledCorrectly() {
        // Sunday 2026-03-29: At 02:00:00 CET, clocks skip forward to 03:00:00 CEST.
        LocalDate dstDate = LocalDate.of(2026, 3, 29); // Sunday
        WorkingHoursWindow hours = new WorkingHoursWindow(DayOfWeek.SUNDAY, LocalTime.of(1, 0), LocalTime.of(5, 0));

        AvailabilityCalculationRequest request = new AvailabilityCalculationRequest(
                businessId,
                staffId,
                dstDate,
                berlinZone,
                Duration.ofMinutes(60),
                Duration.ZERO,
                Duration.ofMinutes(60),
                List.of(hours),
                Collections.emptyList(),
                Collections.emptyList(),
                Instant.MIN
        );

        List<AvailableSlot> slots = AvailabilityEngine.computeAvailableSlots(request);

        // Total shift span is 3 hours: 01:00 CET -> 03:00 CEST -> 04:00 CEST -> 05:00 CEST.
        assertThat(slots).hasSize(3);
        assertThat(slots.get(0).localStartTime().toLocalTime()).isEqualTo(LocalTime.of(1, 0));
        assertThat(slots.get(1).localStartTime().toLocalTime()).isEqualTo(LocalTime.of(3, 0));
        assertThat(slots.get(2).localStartTime().toLocalTime()).isEqualTo(LocalTime.of(4, 0));
    }

    @Test
    @DisplayName("5. Service longer than remaining gap is excluded")
    void serviceLongerThanGapIsExcluded() {
        // Shift: 09:00 - 11:00 (120 min). Booking: 09:00 - 10:15 (75 min).
        // Remaining gap: 10:15 - 11:00 (45 min).
        WorkingHoursWindow hours = new WorkingHoursWindow(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(11, 0));
        ZonedDateTime bStart = ZonedDateTime.of(normalDate, LocalTime.of(9, 0), berlinZone);
        ZonedDateTime bEnd = ZonedDateTime.of(normalDate, LocalTime.of(10, 15), berlinZone);

        TimeInterval booking = new TimeInterval(bStart.toInstant(), bEnd.toInstant());

        // Service requires 60 minutes
        AvailabilityCalculationRequest request = new AvailabilityCalculationRequest(
                businessId,
                staffId,
                normalDate,
                berlinZone,
                Duration.ofMinutes(60),
                Duration.ZERO,
                Duration.ofMinutes(15),
                List.of(hours),
                Collections.emptyList(),
                List.of(booking),
                Instant.MIN
        );

        List<AvailableSlot> slots = AvailabilityEngine.computeAvailableSlots(request);
        // 45 minute gap cannot fit 60 minute service
        assertThat(slots).isEmpty();
    }

    @Test
    @DisplayName("6. Buffer interaction: slot excluded if duration + buffer exceeds gap")
    void bufferInteractionExcludesOverlongSlot() {
        // Shift: 09:00 - 10:00 (60 min gap).
        WorkingHoursWindow hours = new WorkingHoursWindow(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0));

        // Case A: 45 min service + 15 min buffer = 60 min -> Fits!
        AvailabilityCalculationRequest fitRequest = new AvailabilityCalculationRequest(
                businessId,
                staffId,
                normalDate,
                berlinZone,
                Duration.ofMinutes(45),
                Duration.ofMinutes(15),
                Duration.ofMinutes(60),
                List.of(hours),
                Collections.emptyList(),
                Collections.emptyList(),
                Instant.MIN
        );
        List<AvailableSlot> fitSlots = AvailabilityEngine.computeAvailableSlots(fitRequest);
        assertThat(fitSlots).hasSize(1);
        assertThat(fitSlots.get(0).duration()).isEqualTo(Duration.ofMinutes(45));

        // Case B: 45 min service + 20 min buffer = 65 min -> Exceeds 60 min gap!
        AvailabilityCalculationRequest overlongRequest = new AvailabilityCalculationRequest(
                businessId,
                staffId,
                normalDate,
                berlinZone,
                Duration.ofMinutes(45),
                Duration.ofMinutes(20),
                Duration.ofMinutes(60),
                List.of(hours),
                Collections.emptyList(),
                Collections.emptyList(),
                Instant.MIN
        );
        List<AvailableSlot> overlongSlots = AvailabilityEngine.computeAvailableSlots(overlongRequest);
        assertThat(overlongSlots).isEmpty();
    }
}
