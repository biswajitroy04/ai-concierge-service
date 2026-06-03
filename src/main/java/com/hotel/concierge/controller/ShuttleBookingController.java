package com.hotel.concierge.controller;

import com.hotel.concierge.dto.ShuttleBookingRequest;
import com.hotel.concierge.dto.ShuttleBookingResponse;
import com.hotel.concierge.model.ShuttleBooking;
import com.hotel.concierge.service.ShuttleBookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Tag(name = "Airport Shuttle", description = "Airport shuttle transportation booking endpoints")
public class ShuttleBookingController {

    private final ShuttleBookingService shuttleBookingService;

    // -------------------------------------------------------------------------
    // Guest-facing endpoints
    // -------------------------------------------------------------------------

    @PostMapping("/shuttle-bookings")
    @Transactional
    @Operation(summary = "Create a new airport shuttle booking")
    public ResponseEntity<ShuttleBookingResponse> createBooking(
            @Valid @RequestBody ShuttleBookingRequest request,
            @RequestParam Long reservationId) {

        ShuttleBooking booking = shuttleBookingService.createBooking(reservationId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(booking));
    }

    @GetMapping("/shuttle-bookings/{id}")
    @Operation(summary = "Get a shuttle booking by ID")
    public ResponseEntity<ShuttleBookingResponse> getBookingById(@PathVariable Long id) {
        ShuttleBooking booking = shuttleBookingService.getBookingById(id);
        return ResponseEntity.ok(toResponse(booking));
    }

    @GetMapping("/shuttle-bookings/reservation/{reservationId}")
    @Operation(summary = "List all shuttle bookings for a reservation")
    public ResponseEntity<List<ShuttleBookingResponse>> getBookingsByReservation(
            @PathVariable Long reservationId) {

        List<ShuttleBookingResponse> responses = shuttleBookingService
                .getBookingsByReservation(reservationId)
                .stream()
                .map(ShuttleBookingController::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @DeleteMapping("/shuttle-bookings/{id}")
    @Transactional
    @Operation(summary = "Cancel a shuttle booking")
    public ResponseEntity<ShuttleBookingResponse> cancelBooking(
            @PathVariable Long id,
            @RequestParam Long reservationId) {

        ShuttleBooking booking = shuttleBookingService.cancelBooking(id, reservationId);
        return ResponseEntity.ok(toResponse(booking));
    }

    // -------------------------------------------------------------------------
    // Admin endpoints
    // -------------------------------------------------------------------------

    @GetMapping("/admin/shuttle-bookings/hotel/{hotelId}")
    @Operation(summary = "Get all shuttle bookings for a hotel (admin)")
    public ResponseEntity<List<ShuttleBookingResponse>> getBookingsByHotel(
            @PathVariable Long hotelId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate pickupDate) {

        ShuttleBooking.BookingStatus statusEnum = null;
        if (status != null) {
            try {
                statusEnum = ShuttleBooking.BookingStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid booking status: " + status);
            }
        }

        List<ShuttleBookingResponse> responses = shuttleBookingService
                .getBookingsByHotel(hotelId, statusEnum, pickupDate)
                .stream()
                .map(ShuttleBookingController::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @PutMapping("/admin/shuttle-bookings/{id}/status")
    @Transactional
    @Operation(summary = "Update shuttle booking status (admin)")
    public ResponseEntity<ShuttleBookingResponse> updateStatus(
            @PathVariable Long id,
            @RequestParam String status) {

        ShuttleBooking.BookingStatus statusEnum;
        try {
            statusEnum = ShuttleBooking.BookingStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid booking status: " + status);
        }

        ShuttleBooking booking = shuttleBookingService.updateStatus(id, statusEnum);
        return ResponseEntity.ok(toResponse(booking));
    }

    // -------------------------------------------------------------------------
    // Mapping helper
    // -------------------------------------------------------------------------

    private static ShuttleBookingResponse toResponse(ShuttleBooking booking) {
        return ShuttleBookingResponse.builder()
                .id(booking.getId())
                .bookingReference(booking.getBookingReference())
                .pickupLocation(booking.getPickupLocation())
                .dropoffLocation(booking.getDropoffLocation())
                .pickupDatetime(booking.getPickupDatetime())
                .passengerCount(booking.getPassengerCount())
                .contactPhone(booking.getContactPhone())
                .status(booking.getStatus().name())
                .specialInstructions(booking.getSpecialInstructions())
                .reservationId(booking.getReservation().getId())
                .createdAt(booking.getCreatedAt())
                .updatedAt(booking.getUpdatedAt())
                .build();
    }
}
