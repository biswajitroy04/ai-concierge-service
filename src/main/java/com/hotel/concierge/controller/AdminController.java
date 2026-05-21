package com.hotel.concierge.controller;

import com.hotel.concierge.model.*;
import com.hotel.concierge.repository.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Tag(name = "Admin", description = "Administrative endpoints")
public class AdminController {

    private final ReservationRepository reservationRepo;
    private final ConversationRepository conversationRepo;
    private final EscalationTicketRepository escalationRepo;
    private final HousekeepingRequestRepository housekeepingRepo;
    private final SpaBookingRepository spaBookingRepo;
    private final RestaurantBookingRepository restaurantBookingRepo;

    @GetMapping("/reservations/hotel/{hotelId}")
    @Operation(summary = "Get all checked-in reservations for a hotel")
    public ResponseEntity<List<Reservation>> getActiveReservations(@PathVariable Long hotelId) {
        return ResponseEntity.ok(reservationRepo.findCheckedInByHotel(hotelId));
    }

    @GetMapping("/conversations/hotel/{hotelId}")
    @Operation(summary = "Get active conversations for a hotel")
    public ResponseEntity<List<Conversation>> getActiveConversations(@PathVariable Long hotelId) {
        return ResponseEntity.ok(conversationRepo.findActiveByHotel(hotelId));
    }

    @GetMapping("/escalations/hotel/{hotelId}")
    @Operation(summary = "Get active escalation tickets for a hotel")
    public ResponseEntity<List<EscalationTicket>> getEscalations(@PathVariable Long hotelId) {
        return ResponseEntity.ok(escalationRepo.findActiveByHotel(hotelId));
    }

    @PutMapping("/escalations/{ticketId}/resolve")
    @Operation(summary = "Resolve an escalation ticket")
    public ResponseEntity<EscalationTicket> resolveEscalation(
            @PathVariable Long ticketId,
            @RequestParam String resolutionNotes) {
        EscalationTicket ticket = escalationRepo.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));
        ticket.setStatus(EscalationTicket.TicketStatus.RESOLVED);
        ticket.setResolutionNotes(resolutionNotes);
        ticket.setResolvedAt(java.time.LocalDateTime.now());
        return ResponseEntity.ok(escalationRepo.save(ticket));
    }

    @GetMapping("/housekeeping/hotel/{hotelId}")
    @Operation(summary = "Get pending housekeeping requests")
    public ResponseEntity<List<HousekeepingRequest>> getHousekeepingRequests(@PathVariable Long hotelId) {
        return ResponseEntity.ok(housekeepingRepo.findPendingByHotel(hotelId));
    }

    @PutMapping("/housekeeping/{requestId}/status")
    @Operation(summary = "Update housekeeping request status")
    public ResponseEntity<HousekeepingRequest> updateHousekeepingStatus(
            @PathVariable Long requestId,
            @RequestParam String status) {
        HousekeepingRequest request = housekeepingRepo.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));
        request.setStatus(HousekeepingRequest.RequestStatus.valueOf(status.toUpperCase()));
        if ("COMPLETED".equalsIgnoreCase(status)) {
            request.setCompletedAt(java.time.LocalDateTime.now());
        }
        return ResponseEntity.ok(housekeepingRepo.save(request));
    }

    @GetMapping("/spa-bookings/hotel/{hotelId}")
    @Operation(summary = "Get confirmed spa bookings for a hotel")
    public ResponseEntity<List<SpaBooking>> getSpaBookings(@PathVariable Long hotelId) {
        return ResponseEntity.ok(spaBookingRepo.findConfirmedByDateAndHotel(java.time.LocalDate.now(), hotelId));
    }

    @GetMapping("/spa-bookings/all/{hotelId}")
    @Operation(summary = "Get all spa bookings for a hotel")
    public ResponseEntity<List<SpaBooking>> getAllSpaBookings(@PathVariable Long hotelId) {
        return ResponseEntity.ok(spaBookingRepo.findAll());
    }

    @GetMapping("/restaurant-bookings/hotel/{hotelId}")
    @Operation(summary = "Get all restaurant bookings for a hotel")
    public ResponseEntity<List<RestaurantBooking>> getRestaurantBookings(@PathVariable Long hotelId) {
        return ResponseEntity.ok(restaurantBookingRepo.findAll());
    }
}
