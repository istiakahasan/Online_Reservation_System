package com.example.reservation.reservation.service;

import com.example.reservation.reservation.api.dto.CreateReservationRequestDto;
import com.example.reservation.reservation.api.dto.ReservationResponseDto;
import java.util.List;
import java.util.UUID;

public interface ReservationService {
    ReservationResponseDto createReservation(CreateReservationRequestDto request);
    ReservationResponseDto getReservationById(UUID id);
    List<ReservationResponseDto> getCustomerReservations(UUID customerId);
    ReservationResponseDto cancelReservation(UUID id, UUID actorId, String reason);
}
