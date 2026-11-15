package com.example.reservation.availability.domain;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure, deterministic calculation engine for computing unreserved appointment slots.
 * Free of side-effects, Spring framework, or database dependencies.
 */
public final class AvailabilityEngine {

    private AvailabilityEngine() {}

    public static List<AvailableSlot> computeAvailableSlots(AvailabilityCalculationRequest request) {
        if (request == null) {
            return Collections.emptyList();
        }

        LocalDate targetDate = request.date();
        DayOfWeek targetDayOfWeek = targetDate.getDayOfWeek();
        ZoneId zone = request.tenantZoneId();
        Duration duration = request.serviceDuration();
        Duration buffer = request.bufferDuration();
        Duration step = request.slotStep();
        Instant now = request.currentInstant();

        // 1. Filter working hours for this weekday
        List<WorkingHoursWindow> relevantWindows = request.workingHours().stream()
                .filter(w -> w.dayOfWeek() == targetDayOfWeek)
                .toList();

        if (relevantWindows.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. Combine all busy blocks (time-offs and existing reservations)
        List<TimeInterval> busyIntervals = new ArrayList<>();
        busyIntervals.addAll(request.timeOffBlocks());
        busyIntervals.addAll(request.existingBookings());

        List<AvailableSlot> resultSlots = new ArrayList<>();

        // 3. For each working window on the calendar day, project onto timeline
        for (WorkingHoursWindow window : relevantWindows) {
            // Respect tenant timezone and DST transition for the specific local date
            ZonedDateTime windowStartLocal = ZonedDateTime.of(targetDate, window.startTime(), zone);
            ZonedDateTime windowEndLocal = ZonedDateTime.of(targetDate, window.endTime(), zone);

            Instant windowStartInstant = windowStartLocal.toInstant();
            Instant windowEndInstant = windowEndLocal.toInstant();

            Instant candidateStart = windowStartInstant;

            while (true) {
                Instant candidateEnd = candidateStart.plus(duration);
                Instant candidateBufferEnd = candidateEnd.plus(buffer);

                // Check if candidate exceeds shift boundaries
                if (candidateEnd.isAfter(windowEndInstant) || candidateBufferEnd.isAfter(windowEndInstant)) {
                    break;
                }

                // Check rule: Skip past slots
                if (!candidateStart.isBefore(now)) {
                    // Check if candidate overlaps with any busy block
                    boolean isBlocked = false;
                    for (TimeInterval busy : busyIntervals) {
                        // The slot requires freedom from candidateStart to candidateBufferEnd
                        if (busy.overlaps(candidateStart, candidateBufferEnd)) {
                            isBlocked = true;
                            break;
                        }
                    }

                    if (!isBlocked) {
                        ZonedDateTime localSlotStart = candidateStart.atZone(zone);
                        ZonedDateTime localSlotEnd = candidateEnd.atZone(zone);

                        resultSlots.add(new AvailableSlot(
                                request.staffId(),
                                candidateStart,
                                candidateEnd,
                                localSlotStart,
                                localSlotEnd,
                                duration
                        ));
                    }
                }

                candidateStart = candidateStart.plus(step);
            }
        }

        return Collections.unmodifiableList(resultSlots);
    }
}
