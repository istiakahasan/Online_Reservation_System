package com.example.reservation.reservation.service;

import com.example.reservation.reservation.domain.event.ReservationCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Component
public class ReservationEventListener {

    private final List<ReservationCreatedEvent> receivedEvents = new CopyOnWriteArrayList<>();

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReservationCreated(ReservationCreatedEvent event) {
        log.info("Reservation committed successfully [AFTER_COMMIT]: reservationId={}, businessId={}, staffId={}, startTime={}",
                event.reservationId(), event.businessId(), event.staffId(), event.startTime());
        receivedEvents.add(event);
    }

    public List<ReservationCreatedEvent> getReceivedEvents() {
        return List.copyOf(receivedEvents);
    }

    public int getReceivedEventCount() {
        return receivedEvents.size();
    }

    public void reset() {
        receivedEvents.clear();
    }
}
