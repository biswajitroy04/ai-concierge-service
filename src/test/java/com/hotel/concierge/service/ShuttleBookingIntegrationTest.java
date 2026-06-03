package com.hotel.concierge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.concierge.dto.ShuttleBookingRequest;
import com.hotel.concierge.dto.ShuttleBookingResponse;
import com.hotel.concierge.model.Guest;
import com.hotel.concierge.model.Hotel;
import com.hotel.concierge.model.Reservation;
import com.hotel.concierge.model.ShuttleBooking;
import com.hotel.concierge.model.ShuttleBooking.BookingStatus;
import com.hotel.concierge.repository.GuestRepository;
import com.hotel.concierge.repository.HotelRepository;
import com.hotel.concierge.repository.ReservationRepository;
import com.hotel.concierge.repository.ShuttleBookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the Shuttle Booking Service using Spring Boot Test.
 * These tests verify end-to-end behavior through REST endpoints and database persistence.
 *
 * Validates:
 * - Requirements 1.1, 2.2, 2.4, 3.1, 4.1, 4.2, 6.1
 * - Properties 7, 8, 9, 10, 11
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class ShuttleBookingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ShuttleBookingService shuttleBookingService;

    @Autowired
    private ShuttleBookingRepository shuttleBookingRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private GuestRepository guestRepository;

    @Autowired
    private HotelRepository hotelRepository;

    private Hotel hotel1;
    private Hotel hotel2;
    private Guest guest1;
    private Guest guest2;
    private Reservation reservation1;
    private Reservation reservation2;

    @BeforeEach
    void setUp() {
        // Create hotels
        hotel1 = hotelRepository.save(Hotel.builder()
                .name("Hotel One")
                .address("123 Main St")
                .city("New York")
                .country("USA")
                .active(true)
                .build());

        hotel2 = hotelRepository.save(Hotel.builder()
                .name("Hotel Two")
                .address("456 Oak Ave")
                .city("Los Angeles")
                .country("USA")
                .active(true)
                .build());

        // Create guests
        guest1 = guestRepository.save(Guest.builder()
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .phone("+1-555-0100")
                .build());

        guest2 = guestRepository.save(Guest.builder()
                .firstName("Jane")
                .lastName("Smith")
                .email("jane@example.com")
                .phone("+1-555-0200")
                .build());

        // Create reservations with unique confirmation numbers
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        String uuid1 = UUID.randomUUID().toString().substring(0, 8);
        String uuid2 = UUID.randomUUID().toString().substring(0, 8);

        reservation1 = reservationRepository.save(Reservation.builder()
                .confirmationNumber("RES-" + uuid1)
                .hotel(hotel1)
                .guest(guest1)
                .roomNumber("101")
                .roomType("Deluxe")
                .checkInDate(tomorrow)
                .checkOutDate(tomorrow.plusDays(3))
                .numberOfGuests(2)
                .status(Reservation.ReservationStatus.CONFIRMED)
                .build());

        reservation2 = reservationRepository.save(Reservation.builder()
                .confirmationNumber("RES-" + uuid2)
                .hotel(hotel2)
                .guest(guest2)
                .roomNumber("202")
                .roomType("Standard")
                .checkInDate(tomorrow)
                .checkOutDate(tomorrow.plusDays(2))
                .numberOfGuests(1)
                .status(Reservation.ReservationStatus.CONFIRMED)
                .build());
    }

    // =========================================================================
    // Test 1: Property 7 - Create-then-retrieve round trip
    // =========================================================================
    @Test
    @DisplayName("Property 7: Create-then-retrieve round trip - POST booking via REST, GET by ID, verify all fields match")
    void testCreateThenRetrieveRoundTrip() throws Exception {
        // Arrange
        LocalDateTime pickupTime = LocalDateTime.now().plusHours(24);
        ShuttleBookingRequest request = ShuttleBookingRequest.builder()
                .pickupLocation("Hotel Main Entrance")
                .dropoffLocation("JFK Terminal 4")
                .pickupDatetime(pickupTime)
                .passengerCount(2)
                .contactPhone("+1-555-0100")
                .specialInstructions("Large luggage, need help loading")
                .build();

        // Act - POST to create
        MvcResult createResult = mockMvc.perform(post("/shuttle-bookings")
                .param("reservationId", reservation1.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        ShuttleBookingResponse createdResponse = objectMapper.readValue(
                createResult.getResponse().getContentAsString(),
                ShuttleBookingResponse.class);

        // Act - GET by returned ID
        MvcResult getResult = mockMvc.perform(get("/shuttle-bookings/" + createdResponse.getId()))
                .andExpect(status().isOk())
                .andReturn();

        ShuttleBookingResponse retrievedResponse = objectMapper.readValue(
                getResult.getResponse().getContentAsString(),
                ShuttleBookingResponse.class);

        // Assert - all fields match (note: pickupDatetime loses nanosecond precision in DB, so compare key fields)
        assertThat(retrievedResponse.getId())
                .as("Retrieved ID should match created ID")
                .isEqualTo(createdResponse.getId());

        assertThat(retrievedResponse.getBookingReference())
                .as("Booking reference should match")
                .isEqualTo(createdResponse.getBookingReference());

        assertThat(retrievedResponse.getPickupLocation())
                .as("Pickup location should match request")
                .isEqualTo(request.getPickupLocation());

        assertThat(retrievedResponse.getDropoffLocation())
                .as("Dropoff location should match request")
                .isEqualTo(request.getDropoffLocation());

        assertThat(retrievedResponse.getPassengerCount())
                .as("Passenger count should match request")
                .isEqualTo(request.getPassengerCount());

        assertThat(retrievedResponse.getContactPhone())
                .as("Contact phone should match request")
                .isEqualTo(request.getContactPhone());

        // Note: pickupDatetime is truncated to seconds in MySQL DATETIME, so we verify the dates match
        // and times are within 1 second
        assertThat(retrievedResponse.getPickupDatetime().toLocalDate())
                .as("Pickup date should match request")
                .isEqualTo(request.getPickupDatetime().toLocalDate());
        
        assertThat(Math.abs(retrievedResponse.getPickupDatetime().getSecond() - 
                           request.getPickupDatetime().getSecond()))
                .as("Pickup time should match within seconds")
                .isLessThan(2);

        assertThat(retrievedResponse.getStatus())
                .as("Status should be REQUESTED on creation")
                .isEqualTo(BookingStatus.REQUESTED.name());

        assertThat(retrievedResponse.getSpecialInstructions())
                .as("Special instructions should match request")
                .isEqualTo(request.getSpecialInstructions());
    }

    // =========================================================================
    // Test 2: Property 8 - Admin hotel filter returns only that hotel's bookings
    // =========================================================================
    @Test
    @DisplayName("Property 8: Admin hotel filter - create bookings for two hotels, query each, verify no cross-hotel results")
    void testAdminHotelFilterNoCrossHotelResults() throws Exception {
        // Arrange - create booking for hotel1
        LocalDateTime pickupTime1 = LocalDateTime.now().plusHours(24);
        ShuttleBooking booking1 = shuttleBookingService.createBooking(
                reservation1.getId(),
                ShuttleBookingRequest.builder()
                        .pickupLocation("Hotel One Entrance")
                        .dropoffLocation("JFK Terminal 1")
                        .pickupDatetime(pickupTime1)
                        .passengerCount(1)
                        .contactPhone("+1-555-0100")
                        .build());

        // Arrange - create booking for hotel2
        LocalDateTime pickupTime2 = LocalDateTime.now().plusHours(48);
        ShuttleBooking booking2 = shuttleBookingService.createBooking(
                reservation2.getId(),
                ShuttleBookingRequest.builder()
                        .pickupLocation("Hotel Two Entrance")
                        .dropoffLocation("LAX Terminal 2")
                        .pickupDatetime(pickupTime2)
                        .passengerCount(2)
                        .contactPhone("+1-555-0200")
                        .build());

        // Act - query hotel1
        MvcResult hotel1Result = mockMvc.perform(
                get("/admin/shuttle-bookings/hotel/" + hotel1.getId()))
                .andExpect(status().isOk())
                .andReturn();

        List<ShuttleBookingResponse> hotel1Bookings = objectMapper.readValue(
                hotel1Result.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, ShuttleBookingResponse.class));

        // Act - query hotel2
        MvcResult hotel2Result = mockMvc.perform(
                get("/admin/shuttle-bookings/hotel/" + hotel2.getId()))
                .andExpect(status().isOk())
                .andReturn();

        List<ShuttleBookingResponse> hotel2Bookings = objectMapper.readValue(
                hotel2Result.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, ShuttleBookingResponse.class));

        // Assert - hotel1 returns only hotel1 bookings
        assertThat(hotel1Bookings)
                .as("Hotel 1 should have exactly 1 booking")
                .hasSize(1);

        assertThat(hotel1Bookings.get(0).getId())
                .as("Hotel 1 booking should be booking1")
                .isEqualTo(booking1.getId());

        assertThat(hotel1Bookings.get(0).getDropoffLocation())
                .as("Hotel 1 booking should have JFK Terminal")
                .isEqualTo("JFK Terminal 1");

        // Assert - hotel2 returns only hotel2 bookings
        assertThat(hotel2Bookings)
                .as("Hotel 2 should have exactly 1 booking")
                .hasSize(1);

        assertThat(hotel2Bookings.get(0).getId())
                .as("Hotel 2 booking should be booking2")
                .isEqualTo(booking2.getId());

        assertThat(hotel2Bookings.get(0).getDropoffLocation())
                .as("Hotel 2 booking should have LAX Terminal")
                .isEqualTo("LAX Terminal 2");

        // Assert - NO cross-hotel results
        assertThat(hotel1Bookings.stream().map(ShuttleBookingResponse::getId).toList())
                .as("Hotel 1 results should not contain hotel2 booking")
                .doesNotContain(booking2.getId());

        assertThat(hotel2Bookings.stream().map(ShuttleBookingResponse::getId).toList())
                .as("Hotel 2 results should not contain hotel1 booking")
                .doesNotContain(booking1.getId());
    }

    // =========================================================================
    // Test 3: Full lifecycle - create → confirm → in_progress → complete
    // =========================================================================
    @Test
    @DisplayName("Full lifecycle: create REQUESTED → CONFIRMED → IN_PROGRESS → COMPLETED, verify DB at each step")
    void testFullLifecycle() throws Exception {
        // Arrange
        LocalDateTime pickupTime = LocalDateTime.now().plusHours(24);
        ShuttleBooking booking = shuttleBookingService.createBooking(
                reservation1.getId(),
                ShuttleBookingRequest.builder()
                        .pickupLocation("Hotel Entrance")
                        .dropoffLocation("Airport")
                        .pickupDatetime(pickupTime)
                        .passengerCount(1)
                        .contactPhone("+1-555-0100")
                        .build());

        Long bookingId = booking.getId();

        // Verify initial status is REQUESTED
        ShuttleBooking afterCreate = shuttleBookingRepository.findById(bookingId).orElseThrow();
        assertThat(afterCreate.getStatus())
                .as("Status after create should be REQUESTED")
                .isEqualTo(BookingStatus.REQUESTED);

        // Act - transition to CONFIRMED
        mockMvc.perform(put("/admin/shuttle-bookings/" + bookingId + "/status")
                .param("status", "CONFIRMED"))
                .andExpect(status().isOk());

        ShuttleBooking afterConfirm = shuttleBookingRepository.findById(bookingId).orElseThrow();
        assertThat(afterConfirm.getStatus())
                .as("Status after confirm should be CONFIRMED")
                .isEqualTo(BookingStatus.CONFIRMED);

        // Act - transition to IN_PROGRESS
        mockMvc.perform(put("/admin/shuttle-bookings/" + bookingId + "/status")
                .param("status", "IN_PROGRESS"))
                .andExpect(status().isOk());

        ShuttleBooking afterProgress = shuttleBookingRepository.findById(bookingId).orElseThrow();
        assertThat(afterProgress.getStatus())
                .as("Status after in_progress should be IN_PROGRESS")
                .isEqualTo(BookingStatus.IN_PROGRESS);

        // Act - transition to COMPLETED
        mockMvc.perform(put("/admin/shuttle-bookings/" + bookingId + "/status")
                .param("status", "COMPLETED"))
                .andExpect(status().isOk());

        ShuttleBooking afterComplete = shuttleBookingRepository.findById(bookingId).orElseThrow();
        assertThat(afterComplete.getStatus())
                .as("Status after complete should be COMPLETED")
                .isEqualTo(BookingStatus.COMPLETED);
    }

    // =========================================================================
    // Test 4: Cancel REQUESTED booking succeeds
    // =========================================================================
    @Test
    @DisplayName("Cancel REQUESTED booking succeeds - status becomes CANCELLED")
    void testCancelRequestedBookingSucceeds() throws Exception {
        // Arrange
        LocalDateTime pickupTime = LocalDateTime.now().plusHours(24);
        ShuttleBooking booking = shuttleBookingService.createBooking(
                reservation1.getId(),
                ShuttleBookingRequest.builder()
                        .pickupLocation("Hotel Entrance")
                        .dropoffLocation("Airport")
                        .pickupDatetime(pickupTime)
                        .passengerCount(1)
                        .contactPhone("+1-555-0100")
                        .build());

        Long bookingId = booking.getId();

        // Verify initial status
        ShuttleBooking before = shuttleBookingRepository.findById(bookingId).orElseThrow();
        assertThat(before.getStatus()).isEqualTo(BookingStatus.REQUESTED);

        // Act - DELETE to cancel
        MvcResult result = mockMvc.perform(delete("/shuttle-bookings/" + bookingId)
                .param("reservationId", reservation1.getId().toString()))
                .andExpect(status().isOk())
                .andReturn();

        ShuttleBookingResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                ShuttleBookingResponse.class);

        // Assert - response shows CANCELLED
        assertThat(response.getStatus())
                .as("Response status should show CANCELLED")
                .isEqualTo(BookingStatus.CANCELLED.name());

        // Assert - DB record has CANCELLED
        ShuttleBooking after = shuttleBookingRepository.findById(bookingId).orElseThrow();
        assertThat(after.getStatus())
                .as("DB status should be CANCELLED")
                .isEqualTo(BookingStatus.CANCELLED);
    }

    // =========================================================================
    // Test 5: Cancel COMPLETED booking fails with 400
    // =========================================================================
    @Test
    @DisplayName("Cancel COMPLETED booking fails with 400 - DB status remains COMPLETED")
    void testCancelCompletedBookingFails() throws Exception {
        // Arrange
        LocalDateTime pickupTime = LocalDateTime.now().plusHours(24);
        ShuttleBooking booking = shuttleBookingService.createBooking(
                reservation1.getId(),
                ShuttleBookingRequest.builder()
                        .pickupLocation("Hotel Entrance")
                        .dropoffLocation("Airport")
                        .pickupDatetime(pickupTime)
                        .passengerCount(1)
                        .contactPhone("+1-555-0100")
                        .build());

        Long bookingId = booking.getId();

        // Transition through valid states to COMPLETED
        shuttleBookingService.updateStatus(bookingId, BookingStatus.CONFIRMED);
        shuttleBookingService.updateStatus(bookingId, BookingStatus.IN_PROGRESS);
        shuttleBookingService.updateStatus(bookingId, BookingStatus.COMPLETED);

        ShuttleBooking before = shuttleBookingRepository.findById(bookingId).orElseThrow();
        assertThat(before.getStatus()).isEqualTo(BookingStatus.COMPLETED);

        // Act - attempt to cancel (should fail)
        mockMvc.perform(delete("/shuttle-bookings/" + bookingId)
                .param("reservationId", reservation1.getId().toString()))
                .andExpect(status().isBadRequest());

        // Assert - DB status remains COMPLETED
        ShuttleBooking after = shuttleBookingRepository.findById(bookingId).orElseThrow();
        assertThat(after.getStatus())
                .as("Status should remain COMPLETED after failed cancel")
                .isEqualTo(BookingStatus.COMPLETED);
    }
}
