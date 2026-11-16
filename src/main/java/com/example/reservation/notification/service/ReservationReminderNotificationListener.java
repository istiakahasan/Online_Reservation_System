package com.example.reservation.notification.service;

import com.example.reservation.reservation.domain.event.ReservationReminderEvent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationReminderNotificationListener {

    private final List<ReservationReminderEvent> receivedEvents = new CopyOnWriteArrayList<>();

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReservationReminder(ReservationReminderEvent event) {
        log.info("Notification Module received ReservationReminderEvent [AFTER_COMMIT]: reservationId={}, customerId={}, startTime={}",
                event.reservationId(), event.customerId(), event.startTime());
        receivedEvents.add(event);
    }

    public List<ReservationReminderEvent> getReceivedEvents() {
        return List.copyOf(receivedEvents);
    }

    public int getReceivedEventCount() {
        return receivedEvents.size();
    }

    public void reset() {
        receivedEvents.clear();
    }
}
