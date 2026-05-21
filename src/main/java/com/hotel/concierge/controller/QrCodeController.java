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
    @Operation(summary = "List all reservations available for QR generation")
    public ResponseEntity<List<Map<String, Object>>> listReservations() {
        List<Reservation> reservations = reservationRepository.findAll();
        List<Map<String, Object>> result = reservations.stream()
                .map(r -> Map.<String, Object>of(
                        "id", r.getId(),
                        "confirmationNumber", r.getConfirmationNumber(),
                        "guestName", r.getGuest().getFullName(),
                        "roomNumber", r.getRoomNumber(),
                        "checkInDate", r.getCheckInDate().toString(),
                        "checkOutDate", r.getCheckOutDate().toString(),
                        "status", r.getStatus().name()
                ))
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
            return ResponseEntity.ok().body(Map.of(
                    "valid", true,
                    "guestName", reservation.getGuest().getFullName(),
                    "roomNumber", reservation.getRoomNumber(),
                    "confirmationNumber", reservation.getConfirmationNumber()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of(
                    "valid", false,
                    "error", e.getMessage()
            ));
        }
    }
}
