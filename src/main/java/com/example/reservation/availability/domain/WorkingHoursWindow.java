package com.example.reservation.availability.domain;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Objects;

public record WorkingHoursWindow(
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime
) {
    public WorkingHoursWindow {
        Objects.requireNonNull(dayOfWeek, "dayOfWeek must not be null");
        Objects.requireNonNull(startTime, "startTime must not be null");
        Objects.requireNonNull(endTime, "endTime must not be null");
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("Working hours endTime must be after startTime");
        }
    }
}
