package com.hotel.concierge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShuttleBookingRequest {

    @NotBlank(message = "Pickup location is required")
    @Schema(description = "Origin address or hotel area", example = "Hotel Main Entrance")
    private String pickupLocation;

    @NotBlank(message = "Dropoff location is required")
    @Schema(description = "Destination, typically airport and terminal", example = "JFK Terminal 4")
    private String dropoffLocation;

    @NotNull(message = "Pickup date and time is required")
    @Future(message = "Pickup date and time must be in the future")
    @Schema(description = "Pickup date and time (future)", example = "2026-06-15T09:30:00")
    private LocalDateTime pickupDatetime;

    @NotNull(message = "Passenger count is required")
    @Min(value = 1, message = "Passenger count must be at least 1")
    @Max(value = 10, message = "Passenger count must not exceed 10")
    @Schema(description = "Number of passengers (1-10)", example = "2")
    private Integer passengerCount;

    @NotBlank(message = "Contact phone is required")
    @Schema(description = "Guest contact phone for driver", example = "+1-555-0100")
    private String contactPhone;

    @Schema(description = "Optional special instructions", example = "Large luggage, need help loading")
    private String specialInstructions;
}
