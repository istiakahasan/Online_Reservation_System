package com.example.reservation.service.api;

import com.example.reservation.service.service.ServiceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;

@WebMvcTest(ServiceController.class)
@AutoConfigureMockMvc(addFilters = false)
class ServiceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ServiceService serviceService;

    @Test
    @DisplayName("ServiceController context loads successfully")
    void contextLoads() {
        assertThat(mockMvc).isNotNull();
        assertThat(serviceService).isNotNull();
    }
}
