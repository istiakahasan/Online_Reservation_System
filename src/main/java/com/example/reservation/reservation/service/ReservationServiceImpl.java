package com.example.reservation.reservation.service;

import com.example.reservation.reservation.api.dto.CreateReservationRequestDto;
import com.example.reservation.reservation.api.dto.ReservationResponseDto;
import com.example.reservation.reservation.repo.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationServiceImpl implements ReservationService {

    private final ReservationRepository reservationRepository;

    @Override
    @Transactional
    public ReservationResponseDto createReservation(CreateReservationRequestDto request) {
        // Scaffold stub - business logic to be implemented
        return null;
    }

    @Override
    public ReservationResponseDto getReservationById(UUID id) {
        // Scaffold stub - business logic to be implemented
        return null;
    }

    @Override
    public List<ReservationResponseDto> getCustomerReservations(UUID customerId) {
        // Scaffold stub - business logic to be implemented
        return Collections.emptyList();
    }

    @Override
    @Transactional
    public ReservationResponseDto cancelReservation(UUID id, UUID actorId, String reason) {
        // Scaffold stub - business logic to be implemented
        return null;
    }
}
