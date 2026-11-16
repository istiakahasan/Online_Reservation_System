package com.example.reservation.reservation.domain;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

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

    public abstract Set<ReservationStatus> nextAllowedStatuses();

    public boolean canTransitionTo(ReservationStatus target) {
        return target != null && nextAllowedStatuses().contains(target);
    }

    public boolean isTerminal() {
        return nextAllowedStatuses().isEmpty();
    }
}
