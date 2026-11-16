package com.example.reservation.notification.service;

import com.example.reservation.notification.service.channel.EmailChannel;
import com.example.reservation.notification.service.channel.NotificationRequest;
import com.example.reservation.reservation.domain.ReservationEntity;
import com.example.reservation.reservation.domain.event.ReservationCancelled;
import com.example.reservation.reservation.domain.event.ReservationConfirmed;
import com.example.reservation.reservation.domain.event.ReservationCreatedEvent;
import com.example.reservation.reservation.domain.event.ReservationReminderDue;
import com.example.reservation.reservation.repo.ReservationRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final ReservationRepository reservationRepository;
    private final EmailChannel emailChannel;

    private final AtomicInteger createdHandledCount = new AtomicInteger(0);
    private final AtomicInteger confirmedHandledCount = new AtomicInteger(0);
    private final AtomicInteger cancelledHandledCount = new AtomicInteger(0);
    private final AtomicInteger reminderHandledCount = new AtomicInteger(0);

    /**
     * Event 1: ReservationCreated -> send "pending" email
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReservationCreated(ReservationCreatedEvent event) {
        log.info("Processing ReservationCreated notification event: reservationId={}", event.reservationId());
        createdHandledCount.incrementAndGet();

        try {
            Optional<ReservationEntity> reservationOpt = reservationRepository.findById(event.reservationId());
            if (reservationOpt.isEmpty()) {
                log.warn("Reservation not found for created event id={}", event.reservationId());
                return;
            }

            ReservationEntity reservation = reservationOpt.get();
            Map<String, Object> model = new HashMap<>();
            model.put("customerName", reservation.getCustomer().getFirstName() + " " + reservation.getCustomer().getLastName());
            model.put("serviceName", reservation.getService().getName());
            model.put("startTime", reservation.getStartTime().toString());
            model.put("totalPrice", reservation.getTotalPrice());
            model.put("currency", reservation.getCurrency());
            model.put("holdExpiresAt", reservation.getHoldExpiresAt() != null ? reservation.getHoldExpiresAt().toString() : "15 minutes");
            model.put("businessName", reservation.getBusiness().getName());

            NotificationRequest request = new NotificationRequest(
                    reservation.getId(),
                    reservation.getCustomer().getEmail(),
                    "Reservation Pending Payment - " + reservation.getBusiness().getName(),
                    "email/reservation-pending",
                    model
            );

            emailChannel.send(request);
        } catch (Exception ex) {
            log.error("Failed to process ReservationCreated notification for id={}: {}",
                    event.reservationId(), ex.getMessage(), ex);
        }
    }

    /**
     * Event 2: ReservationConfirmed -> send confirmation email
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReservationConfirmed(ReservationConfirmed event) {
        log.info("Processing ReservationConfirmed notification event: reservationId={}", event.reservationId());
        confirmedHandledCount.incrementAndGet();

        try {
            Optional<ReservationEntity> reservationOpt = reservationRepository.findById(event.reservationId());
            if (reservationOpt.isEmpty()) {
                log.warn("Reservation not found for confirmed event id={}", event.reservationId());
                return;
            }

            ReservationEntity reservation = reservationOpt.get();
            Map<String, Object> model = new HashMap<>();
            model.put("customerName", reservation.getCustomer().getFirstName() + " " + reservation.getCustomer().getLastName());
            model.put("reservationId", reservation.getId().toString());
            model.put("serviceName", reservation.getService().getName());
            model.put("staffName", reservation.getStaff().getFirstName() + " " + reservation.getStaff().getLastName());
            model.put("startTime", reservation.getStartTime().toString());
            model.put("businessName", reservation.getBusiness().getName());

            NotificationRequest request = new NotificationRequest(
                    reservation.getId(),
                    reservation.getCustomer().getEmail(),
                    "Reservation Confirmed! - " + reservation.getBusiness().getName(),
                    "email/reservation-confirmed",
                    model
            );

            emailChannel.send(request);
        } catch (Exception ex) {
            log.error("Failed to process ReservationConfirmed notification for id={}: {}",
                    event.reservationId(), ex.getMessage(), ex);
        }
    }

    /**
     * Event 3: ReservationCancelled -> send cancellation email
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReservationCancelled(ReservationCancelled event) {
        log.info("Processing ReservationCancelled notification event: reservationId={}", event.reservationId());
        cancelledHandledCount.incrementAndGet();

        try {
            Optional<ReservationEntity> reservationOpt = reservationRepository.findById(event.reservationId());
            if (reservationOpt.isEmpty()) {
                log.warn("Reservation not found for cancelled event id={}", event.reservationId());
                return;
            }

            ReservationEntity reservation = reservationOpt.get();
            Map<String, Object> model = new HashMap<>();
            model.put("customerName", reservation.getCustomer().getFirstName() + " " + reservation.getCustomer().getLastName());
            model.put("reservationId", reservation.getId().toString());
            model.put("serviceName", reservation.getService().getName());
            model.put("startTime", reservation.getStartTime().toString());
            model.put("reason", event.reason() != null ? event.reason() : "Customer requested cancellation");
            model.put("businessName", reservation.getBusiness().getName());

            NotificationRequest request = new NotificationRequest(
                    reservation.getId(),
                    reservation.getCustomer().getEmail(),
                    "Reservation Cancelled - " + reservation.getBusiness().getName(),
                    "email/reservation-cancelled",
                    model
            );

            emailChannel.send(request);
        } catch (Exception ex) {
            log.error("Failed to process ReservationCancelled notification for id={}: {}",
                    event.reservationId(), ex.getMessage(), ex);
        }
    }

    /**
     * Event 4: ReservationReminderDue -> send reminder email (from scheduler)
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReservationReminderDue(ReservationReminderDue event) {
        log.info("Processing ReservationReminderDue notification event: reservationId={}", event.reservationId());
        reminderHandledCount.incrementAndGet();

        try {
            Optional<ReservationEntity> reservationOpt = reservationRepository.findById(event.reservationId());
            if (reservationOpt.isEmpty()) {
                log.warn("Reservation not found for reminder event id={}", event.reservationId());
                return;
            }

            ReservationEntity reservation = reservationOpt.get();
            Map<String, Object> model = new HashMap<>();
            model.put("customerName", reservation.getCustomer().getFirstName() + " " + reservation.getCustomer().getLastName());
            model.put("serviceName", reservation.getService().getName());
            model.put("staffName", reservation.getStaff().getFirstName() + " " + reservation.getStaff().getLastName());
            model.put("startTime", reservation.getStartTime().toString());
            model.put("businessName", reservation.getBusiness().getName());

            NotificationRequest request = new NotificationRequest(
                    reservation.getId(),
                    reservation.getCustomer().getEmail(),
                    "Upcoming Appointment Reminder - " + reservation.getBusiness().getName(),
                    "email/reservation-reminder",
                    model
            );

            emailChannel.send(request);
        } catch (Exception ex) {
            log.error("Failed to process ReservationReminderDue notification for id={}: {}",
                    event.reservationId(), ex.getMessage(), ex);
        }
    }

    public int getCreatedHandledCount() { return createdHandledCount.get(); }
    public int getConfirmedHandledCount() { return confirmedHandledCount.get(); }
    public int getCancelledHandledCount() { return cancelledHandledCount.get(); }
    public int getReminderHandledCount() { return reminderHandledCount.get(); }

    public void reset() {
        createdHandledCount.set(0);
        confirmedHandledCount.set(0);
        cancelledHandledCount.set(0);
        reminderHandledCount.set(0);
    }
}
