package com.hotel.concierge.service;

import com.hotel.concierge.model.Reservation;
import com.hotel.concierge.model.ShuttleBooking;
import com.hotel.concierge.model.ShuttleBooking.BookingStatus;
import com.hotel.concierge.repository.ReservationRepository;
import com.hotel.concierge.repository.ShuttleBookingRepository;
import com.hotel.concierge.service.ai.tools.ConciergeTools;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.StringLength;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based and unit tests for ConciergeTools shuttle methods using jqwik and Mockito.
 *
 * jqwik's @Property methods are not compatible with @ExtendWith(MockitoExtension.class)
 * lifecycle, so mocks are recreated manually in @BeforeProperty for property tests.
 * Unit tests use standard JUnit 5 + Mockito setup.
 *
 * Each test is tagged: // Feature: airport-shuttle-service, Property/Unit N: <property text>
 */
@ExtendWith(MockitoExtension.class)
class ConciergeToolsShuttleTest {

    private ShuttleBookingRepository shuttleBookingRepository;
    private ReservationRepository reservationRepository;
    private ConciergeTools conciergeTools;

    // =========================================================================
    // PROPERTY TESTS - using jqwik
    // =========================================================================

    @BeforeProperty
    void setupProperty() {
        shuttleBookingRepository = mock(ShuttleBookingRepository.class);
        reservationRepository = mock(ReservationRepository.class);

        conciergeTools = new ConciergeTools(
                1L,
                "test-session-id",
                mock(com.hotel.concierge.repository.HousekeepingRequestRepository.class),
                mock(com.hotel.concierge.repository.SpaBookingRepository.class),
                reservationRepository,
                mock(com.hotel.concierge.repository.EscalationTicketRepository.class),
                mock(com.hotel.concierge.repository.ConversationRepository.class),
                mock(com.hotel.concierge.repository.RestaurantBookingRepository.class),
                shuttleBookingRepository
        );
    }

    // =========================================================================
    // Property 13: Tool confirmation string contains booking reference
    // =========================================================================
    // Feature: airport-shuttle-service, Property 13: Tool confirmation string contains booking reference
    @Property(tries = 100)
    void property13_toolConfirmation_containsBookingReference(
            @ForAll @NotBlank @StringLength(min = 1, max = 100) String pickupLocation,
            @ForAll @NotBlank @StringLength(min = 1, max = 100) String dropoffLocation,
            @ForAll @IntRange(min = 1, max = 10) int passengerCount
    ) {
        // Use a fixed future date/time to avoid flakiness
        LocalDate futureDate = LocalDate.now().plusDays(1);
        LocalTime time = LocalTime.of(14, 30);
        String pickupDateStr = futureDate.format(DateTimeFormatter.ISO_DATE);
        String pickupTimeStr = time.format(DateTimeFormatter.ISO_TIME).substring(0, 5);

        // Mock reservation
        Reservation reservation = Reservation.builder().id(1L).build();
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(reservation));

        // Mock count to return 0 so first booking gets reference SHU-1-001
        when(shuttleBookingRepository.countByReservationId(1L)).thenReturn(0L);

        // Mock save to capture the booking
        AtomicLong capturedId = new AtomicLong(1L);
        when(shuttleBookingRepository.save(any(ShuttleBooking.class))).thenAnswer(invocation -> {
            ShuttleBooking b = invocation.getArgument(0);
            b.setId(capturedId.getAndIncrement());
            return b;
        });

        // Call the tool method
        String result = conciergeTools.bookAirportShuttle(
                pickupLocation,
                dropoffLocation,
                pickupDateStr,
                pickupTimeStr,
                passengerCount,
                "Special instructions"
        );

        // Verify the booking reference is in the correct format and present
        // The booking reference format is SHU-{hotelId}-{sequenceNumber}
        assertThat(result)
                .contains("Booking Reference: SHU-")
                .containsPattern("SHU-\\d+-\\d{3}");
        assertThat(result).contains("SHU-1-001");
    }

    // =========================================================================
    // Property 14: Tool error path produces no booking and returns error message
    // =========================================================================
    // Feature: airport-shuttle-service, Property 14: Tool error path produces no booking and returns error message
    @Property(tries = 100)
    void property14_toolErrorPath_producesNoBookingAndReturnsError(
            @ForAll("invalidInputs") InvalidInput invalidInput
    ) {
        // Mock reservation
        Reservation reservation = Reservation.builder().id(1L).build();
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(reservation));

        // Call the tool method with invalid input
        String result = conciergeTools.bookAirportShuttle(
                invalidInput.pickupLocation,
                invalidInput.dropoffLocation,
                invalidInput.pickupDate,
                invalidInput.pickupTime,
                invalidInput.passengerCount,
                null
        );

        // Verify NO save was called on the mock repository
        verify(shuttleBookingRepository, never()).save(any(ShuttleBooking.class));

        // Verify returned string is non-blank and contains error message
        assertThat(result).isNotBlank();
        assertThat(result.toLowerCase()).containsAnyOf(
                "invalid",
                "must be",
                "between",
                "future",
                "passenger"
        );
    }

    @Provide
    Arbitrary<InvalidInput> invalidInputs() {
        return Arbitraries.of(
                // Past date scenarios
                new InvalidInput(
                        "Hotel Main Entrance",
                        "JFK Terminal 4",
                        LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_DATE),
                        "14:30",
                        2
                ),
                new InvalidInput(
                        "Hotel Main Entrance",
                        "JFK Terminal 4",
                        LocalDate.now().format(DateTimeFormatter.ISO_DATE),
                        "10:00",
                        2
                ),
                // Passenger count too low
                new InvalidInput(
                        "Hotel Main Entrance",
                        "JFK Terminal 4",
                        LocalDate.now().plusDays(1).format(DateTimeFormatter.ISO_DATE),
                        "14:30",
                        0
                ),
                // Passenger count negative
                new InvalidInput(
                        "Hotel Main Entrance",
                        "JFK Terminal 4",
                        LocalDate.now().plusDays(1).format(DateTimeFormatter.ISO_DATE),
                        "14:30",
                        -1
                ),
                // Passenger count too high
                new InvalidInput(
                        "Hotel Main Entrance",
                        "JFK Terminal 4",
                        LocalDate.now().plusDays(1).format(DateTimeFormatter.ISO_DATE),
                        "14:30",
                        11
                ),
                // Passenger count way over limit
                new InvalidInput(
                        "Hotel Main Entrance",
                        "JFK Terminal 4",
                        LocalDate.now().plusDays(1).format(DateTimeFormatter.ISO_DATE),
                        "14:30",
                        100
                )
        );
    }

    /**
     * Helper class to represent invalid input combinations.
     */
    static class InvalidInput {
        String pickupLocation;
        String dropoffLocation;
        String pickupDate;
        String pickupTime;
        int passengerCount;

        InvalidInput(String pickupLocation, String dropoffLocation, String pickupDate,
                     String pickupTime, int passengerCount) {
            this.pickupLocation = pickupLocation;
            this.dropoffLocation = dropoffLocation;
            this.pickupDate = pickupDate;
            this.pickupTime = pickupTime;
            this.passengerCount = passengerCount;
        }
    }

    // =========================================================================
    // UNIT TESTS - using standard JUnit 5 + Mockito
    // =========================================================================

    @BeforeEach
    void setupUnit() {
        MockitoAnnotations.openMocks(this);

        shuttleBookingRepository = mock(ShuttleBookingRepository.class);
        reservationRepository = mock(ReservationRepository.class);

        conciergeTools = new ConciergeTools(
                1L,
                "test-session-id",
                mock(com.hotel.concierge.repository.HousekeepingRequestRepository.class),
                mock(com.hotel.concierge.repository.SpaBookingRepository.class),
                reservationRepository,
                mock(com.hotel.concierge.repository.EscalationTicketRepository.class),
                mock(com.hotel.concierge.repository.ConversationRepository.class),
                mock(com.hotel.concierge.repository.RestaurantBookingRepository.class),
                shuttleBookingRepository
        );
    }

    // =========================================================================
    // Unit Test: cancelAirportShuttle for non-existent reference
    // =========================================================================
    // Feature: airport-shuttle-service, Unit Test: cancelAirportShuttle for non-existent reference
    @Test
    void unitTest_cancelAirportShuttle_nonExistentReference_returnsNotFoundString() {
        // Mock findByBookingReference to return empty Optional
        when(shuttleBookingRepository.findByBookingReference("SHU-999-999"))
                .thenReturn(Optional.empty());

        // Call the tool method
        String result = conciergeTools.cancelAirportShuttle("SHU-999-999");

        // Verify the returned string contains "Booking not found"
        assertThat(result).contains("Booking not found");

        // Verify no save was called
        verify(shuttleBookingRepository, never()).save(any(ShuttleBooking.class));
    }

    // =========================================================================
    // Unit Test: cancelAirportShuttle for COMPLETED booking
    // =========================================================================
    // Feature: airport-shuttle-service, Unit Test: cancelAirportShuttle for COMPLETED booking
    @Test
    void unitTest_cancelAirportShuttle_completedBooking_returnsCannotCancelString() {
        // Create a booking with COMPLETED status
        Reservation reservation = Reservation.builder().id(1L).build();
        ShuttleBooking completedBooking = ShuttleBooking.builder()
                .id(1L)
                .reservation(reservation)
                .bookingReference("SHU-1-001")
                .pickupLocation("Hotel Main Entrance")
                .dropoffLocation("JFK Terminal 4")
                .pickupDatetime(LocalDateTime.now().plusDays(1))
                .passengerCount(2)
                .contactPhone("+1-555-0100")
                .status(BookingStatus.COMPLETED)
                .build();

        // Mock findByBookingReference to return the COMPLETED booking
        when(shuttleBookingRepository.findByBookingReference("SHU-1-001"))
                .thenReturn(Optional.of(completedBooking));

        // Call the tool method
        String result = conciergeTools.cancelAirportShuttle("SHU-1-001");

        // Verify the returned string contains "Cannot cancel"
        assertThat(result).contains("Cannot cancel");

        // Verify the booking status was NOT changed
        assertThat(completedBooking.getStatus()).isEqualTo(BookingStatus.COMPLETED);

        // Verify NO save was called to persist the booking
        verify(shuttleBookingRepository, never()).save(any(ShuttleBooking.class));
    }
}
