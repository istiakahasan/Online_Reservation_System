package com.example.reservation.reservation.api;

import com.example.reservation.payment.api.dto.PaymentIntentResponseDto;
import com.example.reservation.payment.service.StripeService;
import com.example.reservation.reservation.api.dto.CreateReservationRequestDto;
import com.example.reservation.reservation.api.dto.ReservationResponseDto;
import com.example.reservation.reservation.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/reservations", "/api/v1/reservations"})
@RequiredArgsConstructor
@Tag(name = "Reservations", description = "Endpoints for booking, payment processing, retrieving, and cancelling reservations")
public class ReservationController {

    private final ReservationService reservationService;
    private final StripeService stripeService;

    @PostMapping
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Create reservation", description = "Initiates a reservation hold pending payment")
    @ApiResponse(responseCode = "201", description = "Reservation hold created")
    @ApiResponse(responseCode = "409", description = "Slot double-booked or unavailable")
    public ResponseEntity<ReservationResponseDto> createReservation(@Valid @RequestBody CreateReservationRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reservationService.createReservation(request));
    }

    @PostMapping("/{id}/payment-intent")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Create Stripe PaymentIntent", description = "Creates a PaymentIntent tied to the reservation and returns clientSecret")
    @ApiResponse(responseCode = "200", description = "PaymentIntent created successfully")
    @ApiResponse(responseCode = "404", description = "Reservation not found")
    @ApiResponse(responseCode = "409", description = "Reservation is not in PENDING status or hold has expired")
    public ResponseEntity<PaymentIntentResponseDto> createPaymentIntent(@PathVariable UUID id) {
        return ResponseEntity.ok(stripeService.createPaymentIntent(id));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get reservation details", description = "Retrieves reservation by UUID")
    @ApiResponse(responseCode = "200", description = "Reservation found")
    @ApiResponse(responseCode = "404", description = "Reservation not found")
    public ResponseEntity<ReservationResponseDto> getReservationById(@PathVariable UUID id) {
        return ResponseEntity.ok(reservationService.getReservationById(id));
    }

    @GetMapping("/customer/{customerId}")
    @Operation(summary = "List customer reservations", description = "Retrieves all bookings made by a specific customer")
    @ApiResponse(responseCode = "200", description = "Reservations retrieved")
    public ResponseEntity<List<ReservationResponseDto>> getCustomerReservations(@PathVariable UUID customerId) {
        return ResponseEntity.ok(reservationService.getCustomerReservations(customerId));
    }

    @PutMapping("/{id}/cancel")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Cancel reservation", description = "Cancels an active or confirmed reservation")
    @ApiResponse(responseCode = "200", description = "Reservation cancelled")
    @ApiResponse(responseCode = "409", description = "Reservation cannot be cancelled in its current state")
    public ResponseEntity<ReservationResponseDto> cancelReservation(
            @PathVariable UUID id,
            @RequestParam UUID actorId,
            @RequestParam(required = false) String reason
    ) {
        return ResponseEntity.ok(reservationService.cancelReservation(id, actorId, reason));
    }
}
