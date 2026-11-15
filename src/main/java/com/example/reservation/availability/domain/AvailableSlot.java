package com.example.reservation.availability.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.UUID;

public record AvailableSlot(
        UUID staffId,
        Instant startInstant,
        Instant endInstant,
        ZonedDateTime localStartTime,
        ZonedDateTime localEndTime,
        Duration duration
) {}
