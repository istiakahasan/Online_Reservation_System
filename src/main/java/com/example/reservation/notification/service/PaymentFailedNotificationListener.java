package com.example.reservation.notification.service;

import com.example.reservation.payment.domain.event.PaymentFailedEvent;
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
public class PaymentFailedNotificationListener {

    private final List<PaymentFailedEvent> receivedEvents = new CopyOnWriteArrayList<>();

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentFailed(PaymentFailedEvent event) {
        log.warn("Notification Module received PaymentFailedEvent [AFTER_COMMIT]: reservationId={}, customerId={}, reason='{}'",
                event.reservationId(), event.customerId(), event.failureMessage());
        receivedEvents.add(event);
    }

    public List<PaymentFailedEvent> getReceivedEvents() {
        return List.copyOf(receivedEvents);
    }

    public int getReceivedEventCount() {
        return receivedEvents.size();
    }

    public void reset() {
        receivedEvents.clear();
    }
}

