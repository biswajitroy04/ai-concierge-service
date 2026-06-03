package com.hotel.concierge.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hotel.concierge.dto.ShuttleBookingRequest;
import com.hotel.concierge.exception.GlobalExceptionHandler;
import com.hotel.concierge.model.Reservation;
import com.hotel.concierge.model.ShuttleBooking;
import com.hotel.concierge.service.ShuttleBookingService;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.StringLength;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Property-based tests for {@link ShuttleBookingController} using jqwik + standalone MockMvc.
 *
 * jqwik's @Property methods are not compatible with @WebMvcTest / SpringExtension lifecycle,
 * so MockMvc is constructed manually in @BeforeProperty with a mocked service.
 *
 * Each test is tagged: // Feature: airport-shuttle-service, Property N: <property text>
 */
class ShuttleBookingControllerPropertyTest {

    private MockMvc mockMvc;
    private ShuttleBookingService shuttleBookingService;
    private ObjectMapper objectMapper;

    @BeforeProperty
    void setup() {
        shuttleBookingService = mock(ShuttleBookingService.class);

        // Configure ObjectMapper with Java time support
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Use a custom MappingJackson2HttpMessageConverter so standaloneSetup
        // always serializes/deserializes as JSON (avoids XML fallback)
        MappingJackson2HttpMessageConverter jsonConverter =
                new MappingJackson2HttpMessageConverter(objectMapper);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new ShuttleBookingController(shuttleBookingService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(jsonConverter)
                .build();
    }

    // =========================================================================
    // Property 3: HTTP 201 returned for any valid booking request
    // =========================================================================
    // Feature: airport-shuttle-service, Property 3: HTTP 201 returned for any valid booking request
    @Property(tries = 100)
    void property3_validBookingRequest_returns201(
            @ForAll @NotBlank @StringLength(min = 1, max = 100) String pickupLocation,
            @ForAll @NotBlank @StringLength(min = 1, max = 100) String dropoffLocation,
            @ForAll @NotBlank @StringLength(min = 1, max = 20) String contactPhone,
            @ForAll @IntRange(min = 1, max = 10) int passengerCount
    ) throws Exception {

        // Use a fixed future datetime to avoid flakiness
        LocalDateTime futurePickup = LocalDateTime.now().plusDays(1);

        ShuttleBookingRequest request = ShuttleBookingRequest.builder()
                .pickupLocation(pickupLocation)
                .dropoffLocation(dropoffLocation)
                .pickupDatetime(futurePickup)
                .passengerCount(passengerCount)
                .contactPhone(contactPhone)
                .build();

        // Build the mock ShuttleBooking the service will return
        Reservation reservation = Reservation.builder().id(1L).build();
        ShuttleBooking mockBooking = ShuttleBooking.builder()
                .id(1L)
                .reservation(reservation)
                .bookingReference("SHU-1-001")
                .pickupLocation(pickupLocation)
                .dropoffLocation(dropoffLocation)
                .pickupDatetime(futurePickup)
                .passengerCount(passengerCount)
                .contactPhone(contactPhone)
                .status(ShuttleBooking.BookingStatus.REQUESTED)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(shuttleBookingService.createBooking(eq(1L), any(ShuttleBookingRequest.class)))
                .thenReturn(mockBooking);

        MvcResult result = mockMvc.perform(
                        post("/shuttle-bookings")
                                .param("reservationId", "1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .accept(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).contains("bookingReference");
        assertThat(responseBody).contains("REQUESTED");
    }

    // =========================================================================
    // Property 15: Missing required fields always produce HTTP 400 with field map
    // =========================================================================
    // Feature: airport-shuttle-service, Property 15: Missing required fields always produce HTTP 400 with field map
    @Property(tries = 5)
    void property15_missingRequiredField_returns400WithErrorsMap(
            @ForAll("requiredFieldNames") String missingField
    ) throws Exception {

        LocalDateTime futurePickup = LocalDateTime.now().plusDays(1);

        // Build a request with the specific field nulled/blanked out
        ShuttleBookingRequest request = buildRequestWithNulledField(missingField, futurePickup);

        MvcResult result = mockMvc.perform(
                        post("/shuttle-bookings")
                                .param("reservationId", "1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .accept(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();

        // Parse the response body and verify the "errors" map is not empty
        @SuppressWarnings("unchecked")
        Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);
        assertThat(responseMap).containsKey("errors");

        @SuppressWarnings("unchecked")
        Map<String, String> errors = (Map<String, String>) responseMap.get("errors");
        assertThat(errors).isNotEmpty();
    }

    @Provide
    Arbitrary<String> requiredFieldNames() {
        return Arbitraries.of(
                "pickupLocation",
                "dropoffLocation",
                "pickupDatetime",
                "passengerCount",
                "contactPhone"
        );
    }

    /**
     * Builds a ShuttleBookingRequest with one specific required field set to null (or empty
     * string for @NotBlank fields) so that Bean Validation rejects it.
     */
    private ShuttleBookingRequest buildRequestWithNulledField(String missingField,
                                                               LocalDateTime futurePickup) {
        ShuttleBookingRequest.ShuttleBookingRequestBuilder builder = ShuttleBookingRequest.builder()
                .pickupLocation("Hotel Main Entrance")
                .dropoffLocation("JFK Terminal 4")
                .pickupDatetime(futurePickup)
                .passengerCount(2)
                .contactPhone("+1-555-0100");

        switch (missingField) {
            case "pickupLocation"   -> builder.pickupLocation(null);
            case "dropoffLocation"  -> builder.dropoffLocation(null);
            case "pickupDatetime"   -> builder.pickupDatetime(null);
            case "passengerCount"   -> builder.passengerCount(null);
            case "contactPhone"     -> builder.contactPhone(null);
            default -> throw new IllegalArgumentException("Unknown field: " + missingField);
        }

        return builder.build();
    }
}
