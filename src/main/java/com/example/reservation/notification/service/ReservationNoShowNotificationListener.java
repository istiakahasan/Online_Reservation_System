package com.example.reservation.notification.service;

import com.example.reservation.reservation.domain.event.ReservationNoShow;
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
public class ReservationNoShowNotificationListener {

    private final List<ReservationNoShow> receivedEvents = new CopyOnWriteArrayList<>();

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReservationNoShow(ReservationNoShow event) {
        log.info("Notification Module received ReservationNoShow [AFTER_COMMIT]: reservationId={}, actorId={}",
                event.reservationId(), event.actorId());
        receivedEvents.add(event);
    }

    public List<ReservationNoShow> getReceivedEvents() {
        return List.copyOf(receivedEvents);
    }

    public int getReceivedEventCount() {
        return receivedEvents.size();
    }

    public void reset() {
        receivedEvents.clear();
    }
}

