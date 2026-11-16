package com.example.reservation.notification.service.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.example.reservation.notification.domain.NotificationLogEntity;
import com.example.reservation.notification.repo.NotificationLogRepository;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

@ExtendWith(MockitoExtension.class)
class EmailChannelTest {

    @Mock
    private SpringTemplateEngine templateEngine;

    @Mock
    private NotificationLogRepository notificationLogRepository;

    @Mock
    private ObjectProvider<JavaMailSender> mailSenderProvider;

    private EmailChannel emailChannel;

    @BeforeEach
    void setUp() {
        emailChannel = new EmailChannel(templateEngine, notificationLogRepository, mailSenderProvider);
    }

    @Test
    @DisplayName("EmailChannel: Successfully renders template, logs SENT in notification_log")
    void send_Success_LogsSentStatus() {
        UUID reservationId = UUID.randomUUID();
        NotificationRequest request = new NotificationRequest(
                reservationId,
                "client@example.com",
                "Reservation Confirmed",
                "email/reservation-confirmed",
                Map.of("customerName", "Alice", "serviceName", "Spa")
        );

        when(templateEngine.process(eq("email/reservation-confirmed"), any(Context.class)))
                .thenReturn("<html><body>Confirmed!</body></html>");
        when(mailSenderProvider.getIfAvailable()).thenReturn(null); // Simulated email mode

        emailChannel.send(request);

        ArgumentCaptor<NotificationLogEntity> logCaptor = ArgumentCaptor.forClass(NotificationLogEntity.class);
        verify(notificationLogRepository).saveAndFlush(logCaptor.capture());

        NotificationLogEntity savedLog = logCaptor.getValue();
        assertThat(savedLog.getReservationId()).isEqualTo(reservationId);
        assertThat(savedLog.getChannel()).isEqualTo("EMAIL");
        assertThat(savedLog.getTemplate()).isEqualTo("email/reservation-confirmed");
        assertThat(savedLog.getRecipient()).isEqualTo("client@example.com");
        assertThat(savedLog.getStatus()).isEqualTo("SENT");
    }

    @Test
    @DisplayName("EmailChannel: Throws exception on failure to trigger Spring Retry")
    void send_Failure_ThrowsExceptionForRetry() {
        NotificationRequest request = new NotificationRequest(
                UUID.randomUUID(),
                "client@example.com",
                "Failed Email",
                "email/reservation-pending",
                Map.of()
        );

        when(templateEngine.process(any(String.class), any(Context.class)))
                .thenThrow(new RuntimeException("Template processing syntax error"));

        assertThatThrownBy(() -> emailChannel.send(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Template processing syntax error");
    }

    @Test
    @DisplayName("EmailChannel: @Recover logs FAILED status into notification_log table")
    void recover_RecordsFailedStatusInLog() {
        UUID reservationId = UUID.randomUUID();
        NotificationRequest request = new NotificationRequest(
                reservationId,
                "client@example.com",
                "Failed Email",
                "email/reservation-pending",
                Map.of()
        );

        Exception cause = new RuntimeException("SMTP host unreachable after 3 attempts");

        emailChannel.recover(cause, request);

        ArgumentCaptor<NotificationLogEntity> logCaptor = ArgumentCaptor.forClass(NotificationLogEntity.class);
        verify(notificationLogRepository).saveAndFlush(logCaptor.capture());

        NotificationLogEntity savedLog = logCaptor.getValue();
        assertThat(savedLog.getReservationId()).isEqualTo(reservationId);
        assertThat(savedLog.getChannel()).isEqualTo("EMAIL");
        assertThat(savedLog.getStatus()).isEqualTo("FAILED");
        assertThat(savedLog.getErrorMessage()).contains("SMTP host unreachable");
    }
}
