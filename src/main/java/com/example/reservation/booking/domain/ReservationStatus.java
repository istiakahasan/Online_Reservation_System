package com.example.reservation.booking.domain;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Lifecycle statuses for a reservation.
 */
public enum ReservationStatus {
    PENDING {
        @Override
        public Set<ReservationStatus> nextAllowedStatuses() {
            return EnumSet.of(CONFIRMED, CANCELLED);
        }
    },
    CONFIRMED {
        @Override
        public Set<ReservationStatus> nextAllowedStatuses() {
            return EnumSet.of(COMPLETED, CANCELLED, NO_SHOW);
        }
    },
    CANCELLED {
        @Override
        public Set<ReservationStatus> nextAllowedStatuses() {
            return Collections.emptySet();
        }
    },
    COMPLETED {
        @Override
        public Set<ReservationStatus> nextAllowedStatuses() {
            return Collections.emptySet();
        }
    },
    NO_SHOW {
        @Override
        public Set<ReservationStatus> nextAllowedStatuses() {
            return Collections.emptySet();
        }
    };

    /**
     * Returns the set of valid next statuses reachable from this status.
     */
    public abstract Set<ReservationStatus> nextAllowedStatuses();

    /**
     * Checks if transitioning from this status to the given target status is valid.
     */
    public boolean canTransitionTo(ReservationStatus target) {
        return target != null && nextAllowedStatuses().contains(target);
    }

    /**
     * Returns true if this is a terminal status from which no further transitions are possible.
     */
    public boolean isTerminal() {
        return nextAllowedStatuses().isEmpty();
    }
}
