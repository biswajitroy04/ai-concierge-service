package com.hotel.concierge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShuttleBookingResponse {

    @Schema(description = "Booking database ID")
    private Long id;

    @Schema(description = "Unique booking reference", example = "SHU-42-001")
    private String bookingReference;

    @Schema(description = "Origin address or hotel area", example = "Hotel Main Entrance")
    private String pickupLocation;

    @Schema(description = "Destination, typically airport and terminal", example = "JFK Terminal 4")
    private String dropoffLocation;

    @Schema(description = "Pickup date and time", example = "2026-06-15T09:30:00")
    private LocalDateTime pickupDatetime;

    @Schema(description = "Number of passengers (1-10)", example = "2")
    private Integer passengerCount;

    @Schema(description = "Guest contact phone for driver", example = "+1-555-0100")
    private String contactPhone;

    @Schema(description = "Booking lifecycle status", example = "REQUESTED")
    private String status;

    @Schema(description = "Optional special instructions", example = "Large luggage, need help loading")
    private String specialInstructions;

    @Schema(description = "ID of the reservation this booking belongs to")
    private Long reservationId;

    @Schema(description = "Timestamp when the booking was created")
    private LocalDateTime createdAt;

    @Schema(description = "Timestamp when the booking was last updated")
    private LocalDateTime updatedAt;
}
