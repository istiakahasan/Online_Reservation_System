package com.example.reservation.staff.api;

import com.example.reservation.staff.service.StaffService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;

@WebMvcTest(StaffController.class)
@AutoConfigureMockMvc(addFilters = false)
class StaffControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StaffService staffService;

    @Test
    @DisplayName("StaffController context loads")
    void contextLoads() {
        assertThat(mockMvc).isNotNull();
        assertThat(staffService).isNotNull();
    }
}
