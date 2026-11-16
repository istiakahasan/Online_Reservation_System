package com.example.reservation.notification.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.reservation.auth.service.JwtService;
import com.example.reservation.notification.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(NotificationController.class)
@AutoConfigureMockMvc(addFilters = false)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private NotificationService notificationService;

    @Test
    @DisplayName("NotificationController context loads")
    void contextLoads() {
        assertThat(mockMvc).isNotNull();
        assertThat(notificationService).isNotNull();
    }
}
