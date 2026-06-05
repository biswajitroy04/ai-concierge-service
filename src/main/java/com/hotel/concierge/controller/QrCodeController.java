package com.hotel.concierge.controller;

import com.hotel.concierge.dto.QrCodeResponse;
import com.hotel.concierge.model.Reservation;
import com.hotel.concierge.repository.ReservationRepository;
import com.hotel.concierge.service.qr.QrCodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/qr")
@RequiredArgsConstructor
@Tag(name = "QR Code", description = "QR code generation and validation")
public class QrCodeController {

    private final QrCodeService qrCodeService;
    private final ReservationRepository reservationRepository;

    @GetMapping("/reservations")
    @Operation(summary = "List reservations where today falls between check-in and check-out dates")
    public ResponseEntity<List<Map<String, Object>>> listReservations() {
        List<Reservation> reservations = reservationRepository.findCurrentReservations(LocalDate.now());
        List<Map<String, Object>> result = reservations.stream()
                .map(r -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", r.getId());
                    item.put("confirmationNumber", r.getConfirmationNumber());
                    item.put("guestName", r.getGuest().getFullName());
                    item.put("guestPhone", r.getGuest().getPhone());
                    item.put("roomNumber", r.getRoomNumber());
                    item.put("checkInDate", r.getCheckInDate().toString());
                    item.put("checkOutDate", r.getCheckOutDate().toString());
                    item.put("status", r.getStatus().name());
                    return item;
                })
                .toList();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/generate/{reservationId}")
    @Operation(summary = "Generate QR code for a reservation")
    public ResponseEntity<QrCodeResponse> generateQrCode(@PathVariable Long reservationId) {
        QrCodeResponse response = qrCodeService.generateQrCode(reservationId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/validate")
    @Operation(summary = "Validate a QR token")
    public ResponseEntity<?> validateToken(@RequestParam String token) {
        try {
            var reservation = qrCodeService.validateQrToken(token);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("valid", true);
            body.put("guestName", reservation.getGuest().getFullName());
            body.put("guestPhone", reservation.getGuest().getPhone());
            body.put("roomNumber", reservation.getRoomNumber());
            body.put("confirmationNumber", reservation.getConfirmationNumber());
            return ResponseEntity.ok().body(body);
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of(
                    "valid", false,
                    "error", e.getMessage()
            ));
        }
    }
}
