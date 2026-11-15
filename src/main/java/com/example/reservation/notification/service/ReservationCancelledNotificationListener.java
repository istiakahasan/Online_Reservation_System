package com.example.reservation.notification.service;

import com.example.reservation.reservation.domain.event.ReservationCancelled;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationCancelledNotificationListener {

    private final List<ReservationCancelled> receivedEvents = new CopyOnWriteArrayList<>();

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReservationCancelled(ReservationCancelled event) {
        log.info("Notification Module received ReservationCancelled [AFTER_COMMIT]: reservationId={}, businessId={}, actorId={}, reason='{}'",
                event.reservationId(), event.businessId(), event.actorId(), event.reason());
        receivedEvents.add(event);
    }

    public List<ReservationCancelled> getReceivedEvents() {
        return List.copyOf(receivedEvents);
    }

    public int getReceivedEventCount() {
        return receivedEvents.size();
    }

    public void reset() {
        receivedEvents.clear();
    }
}

