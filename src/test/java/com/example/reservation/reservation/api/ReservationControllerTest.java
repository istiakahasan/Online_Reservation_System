package com.example.reservation.reservation.api;

import com.example.reservation.reservation.api.dto.CreateReservationRequestDto;
import com.example.reservation.reservation.api.dto.ReservationResponseDto;
import com.example.reservation.reservation.domain.exception.SlotAlreadyBookedException;
import com.example.reservation.reservation.service.ReservationService;
import com.example.reservation.shared.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReservationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ReservationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ReservationService reservationService;

    @Test
    @DisplayName("POST /api/reservations - returns 201 Created on successful booking")
    void createReservation_Success_Returns201() throws Exception {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        Instant startTime = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        Instant endTime = startTime.plus(60, ChronoUnit.MINUTES);

        CreateReservationRequestDto request = new CreateReservationRequestDto(
                businessId,
                customerId,
                serviceId,
                staffId,
                startTime,
                "Need quiet room"
        );

        ReservationResponseDto responseDto = new ReservationResponseDto(
                UUID.randomUUID(),
                businessId,
                customerId,
                serviceId,
                staffId,
                startTime,
                endTime,
                "PENDING",
                new BigDecimal("75.00"),
                "USD",
                Instant.now().plus(10, ChronoUnit.MINUTES),
                "Need quiet room"
        );

        when(reservationService.createReservation(any(CreateReservationRequestDto.class)))
                .thenReturn(responseDto);

        mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(responseDto.id().toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalPrice").value(75.00))
                .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    @DisplayName("POST /api/reservations - returns 409 Conflict when slot is double-booked")
    void createReservation_Collision_Returns409() throws Exception {
        CreateReservationRequestDto request = new CreateReservationRequestDto(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS),
                "VIP Client"
        );

        when(reservationService.createReservation(any(CreateReservationRequestDto.class)))
                .thenThrow(new SlotAlreadyBookedException("Requested slot has already been booked by another transaction"));

        mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.errorCode").value("SLOT_ALREADY_BOOKED"));
    }

    @Test
    @DisplayName("POST /api/reservations - returns 400 Bad Request when required fields are missing")
    void createReservation_ValidationFailure_Returns400() throws Exception {
        // Missing businessId, customerId, serviceId, staffId, startTime
        String invalidJson = "{\"notes\":\"Incomplete request\"}";

        mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }
}
