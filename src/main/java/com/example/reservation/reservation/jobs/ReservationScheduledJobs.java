package com.example.reservation.reservation.jobs;

import com.example.reservation.reservation.domain.ReservationEntity;
import com.example.reservation.reservation.domain.ReservationStateMachine;
import com.example.reservation.reservation.domain.ReservationStatus;
import com.example.reservation.reservation.domain.event.ReservationCancelled;
import com.example.reservation.reservation.domain.event.ReservationNoShow;
import com.example.reservation.reservation.domain.event.ReservationReminderEvent;
import com.example.reservation.reservation.repo.ReservationRepository;
import com.example.reservation.user.domain.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationScheduledJobs {

    public static final UUID SYSTEM_ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final ReservationRepository reservationRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Job 1: expirePendingReservations
     * Runs every minute.
     * Finds PENDING reservations that have exceeded the 15-minute hold threshold,
     * transitions them to CANCELLED via the domain state machine,
     * and publishes ReservationCancelled domain events.
     */
    @Scheduled(cron = "${application.jobs.expire-pending.cron:0 * * * * *}")
    @SchedulerLock(name = "expirePendingReservations", lockAtMostFor = "5m", lockAtLeastFor = "30s")
    @Transactional
    public int expirePendingReservations() {
        long startTime = System.currentTimeMillis();
        Instant now = Instant.now();
        Instant fifteenMinThreshold = now.minus(15, ChronoUnit.MINUTES);

        List<ReservationEntity> expiredReservations = reservationRepository.findPendingForExpiration(
                ReservationStatus.PENDING, now, fifteenMinThreshold
        );

        int count = 0;
        for (ReservationEntity reservation : expiredReservations) {
            try {
                ReservationStateMachine stateMachine = ReservationStateMachine.of(reservation);
                ReservationCancelled event = stateMachine.cancel(
                        SYSTEM_ACTOR_ID,
                        UserRole.ADMIN,
                        "Pending reservation hold expired automatically"
                );

                reservation.setStatus(stateMachine.getCurrentStatus());
                reservationRepository.save(reservation);
                eventPublisher.publishEvent(event);
                count++;
            } catch (Exception ex) {
                log.error("Failed to expire pending reservation id={}: {}", reservation.getId(), ex.getMessage(), ex);
            }
        }

        long durationMs = System.currentTimeMillis() - startTime;
        log.info("Scheduled job completed: job={}, processedCount={}, durationMs={}",
                "expirePendingReservations", count, durationMs);
        return count;
    }

    /**
     * Job 2: sendReminders
     * Runs every hour.
     * Finds CONFIRMED reservations starting in approximately 24 hours (23h - 25h window)
     * that haven't received a reminder yet (idempotent via reminder_sent_at),
     * updates reminder_sent_at, and publishes ReservationReminderEvent.
     */
    @Scheduled(cron = "${application.jobs.send-reminders.cron:0 0 * * * *}")
    @SchedulerLock(name = "sendReminders", lockAtMostFor = "30m", lockAtLeastFor = "5m")
    @Transactional
    public int sendReminders() {
        long startTime = System.currentTimeMillis();
        Instant now = Instant.now();
        Instant windowStart = now.plus(23, ChronoUnit.HOURS);
        Instant windowEnd = now.plus(25, ChronoUnit.HOURS);

        List<ReservationEntity> reminderReservations = reservationRepository.findConfirmedForReminder(
                ReservationStatus.CONFIRMED, windowStart, windowEnd
        );

        int count = 0;
        for (ReservationEntity reservation : reminderReservations) {
            try {
                reservation.setReminderSentAt(now);
                reservationRepository.save(reservation);

                ReservationReminderEvent event = new ReservationReminderEvent(
                        reservation.getId(),
                        reservation.getBusiness().getId(),
                        reservation.getCustomer().getId(),
                        reservation.getStaff().getId(),
                        reservation.getStartTime(),
                        now
                );
                eventPublisher.publishEvent(event);
                count++;
            } catch (Exception ex) {
                log.error("Failed to dispatch reminder for reservation id={}: {}", reservation.getId(), ex.getMessage(), ex);
            }
        }

        long durationMs = System.currentTimeMillis() - startTime;
        log.info("Scheduled job completed: job={}, processedCount={}, durationMs={}",
                "sendReminders", count, durationMs);
        return count;
    }

    /**
     * Job 3: markNoShows
     * Runs every 15 minutes.
     * Finds CONFIRMED reservations where startTime + 1 hour has passed,
     * transitions them to NO_SHOW via the domain state machine,
     * and publishes ReservationNoShow domain events.
     */
    @Scheduled(cron = "${application.jobs.mark-no-shows.cron:0 */15 * * * *}")
    @SchedulerLock(name = "markNoShows", lockAtMostFor = "10m", lockAtLeastFor = "2m")
    @Transactional
    public int markNoShows() {
        long startTime = System.currentTimeMillis();
        Instant now = Instant.now();
        Instant oneHourCutoff = now.minus(1, ChronoUnit.HOURS);

        List<ReservationEntity> pastDueReservations = reservationRepository.findConfirmedPastStartTime(
                ReservationStatus.CONFIRMED, oneHourCutoff
        );

        int count = 0;
        for (ReservationEntity reservation : pastDueReservations) {
            try {
                ReservationStateMachine stateMachine = ReservationStateMachine.of(reservation);
                ReservationNoShow event = stateMachine.markNoShow(SYSTEM_ACTOR_ID, UserRole.ADMIN);

                reservation.setStatus(stateMachine.getCurrentStatus());
                reservationRepository.save(reservation);
                eventPublisher.publishEvent(event);
                count++;
            } catch (Exception ex) {
                log.error("Failed to mark reservation as NO_SHOW id={}: {}", reservation.getId(), ex.getMessage(), ex);
            }
        }

        long durationMs = System.currentTimeMillis() - startTime;
        log.info("Scheduled job completed: job={}, processedCount={}, durationMs={}",
                "markNoShows", count, durationMs);
        return count;
    }
}

