package com.hotel.concierge.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hotel.concierge.dto.ShuttleBookingRequest;
import com.hotel.concierge.exception.GlobalExceptionHandler;
import com.hotel.concierge.exception.ShuttleBookingAccessDeniedException;
import com.hotel.concierge.exception.ShuttleBookingNotFoundException;
import com.hotel.concierge.model.Reservation;
import com.hotel.concierge.model.ShuttleBooking;
import com.hotel.concierge.model.ShuttleBooking.BookingStatus;
import com.hotel.concierge.service.ShuttleBookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for {@link ShuttleBookingController} using standalone MockMvc with Mockito.
 *
 * Uses standaloneSetup to avoid Spring Security complexity and wires in the
 * {@link GlobalExceptionHandler} so that exception-to-status mappings are exercised.
 *
 * Requirements: 2.3, 3.4, 4.4
 */
@ExtendWith(MockitoExtension.class)
class ShuttleBookingControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private ShuttleBookingService shuttleBookingService;

    @BeforeEach
    void setup() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Configure MockMvc to use the same Jackson configuration so that
        // LocalDateTime fields are serialized as ISO strings, not arrays.
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);

        ShuttleBookingController controller = new ShuttleBookingController(shuttleBookingService);
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(converter)
                .build();
    }

    // -------------------------------------------------------------------------
    // Helper: build a fully-populated ShuttleBooking stub
    // -------------------------------------------------------------------------

    private ShuttleBooking buildBookingStub(Long id, Long reservationId, BookingStatus status) {
        Reservation reservation = Reservation.builder()
                .id(reservationId)
                .confirmationNumber("CONF-" + reservationId)
                .build();

        return ShuttleBooking.builder()
                .id(id)
                .reservation(reservation)
                .bookingReference("SHU-" + reservationId + "-001")
                .pickupLocation("Hotel Main Entrance")
                .dropoffLocation("JFK Terminal 4")
                .pickupDatetime(LocalDateTime.now().plusDays(1))
                .passengerCount(2)
                .contactPhone("+1-555-0100")
                .status(status)
                .specialInstructions("Large luggage")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // -------------------------------------------------------------------------
    // Test 1: Non-existent booking ID → 404
    // Validates: Requirement 2.3
    // -------------------------------------------------------------------------

    @Test
    void getBookingById_nonExistentId_returns404() throws Exception {
        when(shuttleBookingService.getBookingById(99L))
                .thenThrow(new ShuttleBookingNotFoundException("Shuttle booking not found: 99"));

        mockMvc.perform(get("/shuttle-bookings/99"))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // Test 2: Invalid status value on PUT → 400
    // Validates: Requirement 3.4
    // -------------------------------------------------------------------------

    @Test
    void updateStatus_invalidStatusValue_returns400() throws Exception {
        // Controller throws IllegalArgumentException before calling service,
        // which is caught by GlobalExceptionHandler as a RuntimeException → 400
        mockMvc.perform(put("/admin/shuttle-bookings/1/status")
                        .param("status", "BLAH"))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // Test 3: Cross-reservation cancel → 403
    // Validates: Requirement 4.4
    // -------------------------------------------------------------------------

    @Test
    void cancelBooking_crossReservation_returns403() throws Exception {
        when(shuttleBookingService.cancelBooking(1L, 99L))
                .thenThrow(new ShuttleBookingAccessDeniedException(
                        "You do not have permission to cancel this booking"));

        mockMvc.perform(delete("/shuttle-bookings/1")
                        .param("reservationId", "99"))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // Test 4: Admin hotel filter returns 200 with list
    // Validates: Requirement 2.4
    // -------------------------------------------------------------------------

    @Test
    void getBookingsByHotel_returnsOkWithList() throws Exception {
        ShuttleBooking stub = buildBookingStub(1L, 1L, BookingStatus.CONFIRMED);

        when(shuttleBookingService.getBookingsByHotel(eq(1L), any(), any()))
                .thenReturn(List.of(stub));

        mockMvc.perform(get("/admin/shuttle-bookings/hotel/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("CONFIRMED"));
    }

    // -------------------------------------------------------------------------
    // Test 5: Create booking returns 201 with REQUESTED status
    // Validates: Requirement 1.3
    // -------------------------------------------------------------------------

    @Test
    void createBooking_validRequest_returns201WithRequestedStatus() throws Exception {
        ShuttleBookingRequest request = ShuttleBookingRequest.builder()
                .pickupLocation("Hotel Main Entrance")
                .dropoffLocation("JFK Terminal 4")
                .pickupDatetime(LocalDateTime.now().plusDays(1))
                .passengerCount(2)
                .contactPhone("+1-555-0100")
                .build();

        ShuttleBooking stub = buildBookingStub(1L, 1L, BookingStatus.REQUESTED);

        when(shuttleBookingService.createBooking(any(), any())).thenReturn(stub);

        mockMvc.perform(post("/shuttle-bookings")
                        .param("reservationId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REQUESTED"));
    }

    // -------------------------------------------------------------------------
    // Test 6: Get booking by ID returns 200
    // Validates: Requirement 2.2
    // -------------------------------------------------------------------------

    @Test
    void getBookingById_existingId_returns200() throws Exception {
        ShuttleBooking stub = buildBookingStub(1L, 1L, BookingStatus.CONFIRMED);

        when(shuttleBookingService.getBookingById(1L)).thenReturn(stub);

        mockMvc.perform(get("/shuttle-bookings/1"))
                .andExpect(status().isOk());
    }
}
