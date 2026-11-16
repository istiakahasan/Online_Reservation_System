package com.example.reservation.business.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.reservation.business.service.BusinessService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BusinessController.class)
@AutoConfigureMockMvc(addFilters = false)
class BusinessControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BusinessService businessService;

    @Test
    @DisplayName("BusinessController context loads successfully")
    void contextLoads() {
        assertThat(mockMvc).isNotNull();
        assertThat(businessService).isNotNull();
    }
}
