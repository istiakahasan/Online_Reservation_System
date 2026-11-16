package com.example.reservation.availability.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.reservation.availability.service.AvailabilityService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AvailabilityController.class)
@AutoConfigureMockMvc(addFilters = false)
class AvailabilityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AvailabilityService availabilityService;

    @Test
    @DisplayName("AvailabilityController context loads successfully")
    void contextLoads() {
        assertThat(mockMvc).isNotNull();
        assertThat(availabilityService).isNotNull();
    }
}
