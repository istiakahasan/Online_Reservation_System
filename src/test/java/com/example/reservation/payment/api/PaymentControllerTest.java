package com.example.reservation.payment.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.reservation.auth.service.JwtService;
import com.example.reservation.payment.service.PaymentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PaymentController.class)
@AutoConfigureMockMvc(addFilters = false)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private PaymentService paymentService;

    @Test
    @DisplayName("PaymentController context loads")
    void contextLoads() {
        assertThat(mockMvc).isNotNull();
        assertThat(paymentService).isNotNull();
    }
}
