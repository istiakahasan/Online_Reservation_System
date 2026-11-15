package com.example.reservation.availability.domain;

import java.time.Instant;
import java.util.Objects;

public record TimeInterval(Instant start, Instant end) {

    public TimeInterval {
        Objects.requireNonNull(start, "start instant must not be null");
        Objects.requireNonNull(end, "end instant must not be null");
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("Interval end [%s] must be strictly after start [%s]".formatted(end, start));
        }
    }

    public boolean overlaps(TimeInterval other) {
        return this.start.isBefore(other.end) && other.start.isBefore(this.end);
    }

    public boolean overlaps(Instant otherStart, Instant otherEnd) {
        return this.start.isBefore(otherEnd) && otherStart.isBefore(this.end);
    }
}
