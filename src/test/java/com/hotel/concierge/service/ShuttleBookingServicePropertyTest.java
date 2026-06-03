package com.hotel.concierge.service;

import com.hotel.concierge.dto.ShuttleBookingRequest;
import com.hotel.concierge.exception.InvalidStatusTransitionException;
import com.hotel.concierge.exception.ShuttleBookingAccessDeniedException;
import com.hotel.concierge.model.Reservation;
import com.hotel.concierge.model.ShuttleBooking;
import com.hotel.concierge.model.ShuttleBooking.BookingStatus;
import com.hotel.concierge.repository.ReservationRepository;
import com.hotel.concierge.repository.ShuttleBookingRepository;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.StringLength;
import net.jqwik.api.lifecycle.BeforeProperty;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Property-based tests for {@link ShuttleBookingService} using jqwik.
 *
 * Mocks are created manually via Mockito.mock() in @BeforeProperty because jqwik
 * manages its own lifecycle independently of JUnit 5 extensions like MockitoExtension.
 *
 * Each test is tagged: // Feature: airport-shuttle-service, Property N: <property text>
 */
class ShuttleBookingServicePropertyTest {

    // Mocks re-initialised before each @Property run
    private ShuttleBookingRepository shuttleBookingRepository;
    private ReservationRepository reservationRepository;
    private ShuttleBookingService service;

    @BeforeProperty
    void initMocks() {
        shuttleBookingRepository = mock(ShuttleBookingRepository.class);
        reservationRepository = mock(ReservationRepository.class);
        service = new ShuttleBookingService(shuttleBookingRepository, reservationRepository);
    }

    // =========================================================================
    // Property 1: Valid booking creation stores REQUESTED status
    // =========================================================================
    // Feature: airport-shuttle-service, Property 1: Valid booking creation stores REQUESTED status
    @Property(tries = 100)
    void property1_validBookingCreation_storesRequestedStatus(
            @ForAll @NotBlank @StringLength(min = 1, max = 100) String pickupLocation,
            @ForAll @NotBlank @StringLength(min = 1, max = 100) String dropoffLocation,
            @ForAll @IntRange(min = 1, max = 10) int passengerCount,
            @ForAll @NotBlank @StringLength(min = 1, max = 20) String contactPhone
    ) {
        // Use a fixed future datetime to avoid flakiness
        LocalDateTime futurePickup = LocalDateTime.now().plusDays(1);

        ShuttleBookingRequest request = ShuttleBookingRequest.builder()
                .pickupLocation(pickupLocation)
                .dropoffLocation(dropoffLocation)
                .pickupDatetime(futurePickup)
                .passengerCount(passengerCount)
                .contactPhone(contactPhone)
                .build();

        Reservation reservation = Reservation.builder().id(1L).build();
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(reservation));
        when(shuttleBookingRepository.countByReservationId(1L)).thenReturn(0L);

        // Simulate save returning the booking that was passed in (with fields set)
        when(shuttleBookingRepository.save(any(ShuttleBooking.class))).thenAnswer(invocation -> {
            ShuttleBooking b = invocation.getArgument(0);
            b.setId(1L);
            return b;
        });

        ShuttleBooking result = service.createBooking(1L, request);

        assertThat(result.getStatus()).isEqualTo(BookingStatus.REQUESTED);
        assertThat(result.getPickupLocation()).isEqualTo(pickupLocation);
        assertThat(result.getDropoffLocation()).isEqualTo(dropoffLocation);
        assertThat(result.getPassengerCount()).isEqualTo(passengerCount);
        assertThat(result.getContactPhone()).isEqualTo(contactPhone);
    }

    // =========================================================================
    // Property 2: Booking reference format and uniqueness
    // =========================================================================
    // Feature: airport-shuttle-service, Property 2: Booking reference format and uniqueness
    @Property(tries = 20)
    void property2_bookingReferenceFormatAndUniqueness(
            @ForAll @IntRange(min = 2, max = 5) int n
    ) {
        Long reservationId = 42L;
        Reservation reservation = Reservation.builder().id(reservationId).build();
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));

        AtomicLong counter = new AtomicLong(0);
        when(shuttleBookingRepository.countByReservationId(reservationId))
                .thenAnswer(inv -> counter.getAndIncrement());

        when(shuttleBookingRepository.save(any(ShuttleBooking.class))).thenAnswer(invocation -> {
            ShuttleBooking b = invocation.getArgument(0);
            b.setId(counter.get());
            return b;
        });

        Set<String> refs = new HashSet<>();
        for (int i = 0; i < n; i++) {
            ShuttleBookingRequest request = ShuttleBookingRequest.builder()
                    .pickupLocation("Hotel")
                    .dropoffLocation("Airport")
                    .pickupDatetime(LocalDateTime.now().plusDays(i + 1))
                    .passengerCount(1)
                    .contactPhone("+1-555-0100")
                    .build();

            ShuttleBooking booking = service.createBooking(reservationId, request);
            String ref = booking.getBookingReference();

            assertThat(ref).matches("SHU-\\d+-\\d+");
            refs.add(ref);
        }

        // All references are distinct
        assertThat(refs).hasSize(n);
    }

    // =========================================================================
    // Property 4: Past pickup datetime always rejected
    // =========================================================================
    // Feature: airport-shuttle-service, Property 4: Past pickup datetime always rejected
    @Property(tries = 100)
    void property4_pastPickupDatetime_alwaysRejected(
            @ForAll("pastDatetimes") LocalDateTime pastDatetime
    ) {
        ShuttleBookingRequest request = ShuttleBookingRequest.builder()
                .pickupLocation("Hotel")
                .dropoffLocation("Airport")
                .pickupDatetime(pastDatetime)
                .passengerCount(2)
                .contactPhone("+1-555-0100")
                .build();

        Reservation reservation = Reservation.builder().id(1L).build();
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> service.createBooking(1L, request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Provide
    Arbitrary<LocalDateTime> pastDatetimes() {
        return Arbitraries.longs()
                .between(1L, 1000L)
                .map(days -> LocalDateTime.now().minusDays(days));
    }

    // =========================================================================
    // Property 5: Special instructions round-trip
    // =========================================================================
    // Feature: airport-shuttle-service, Property 5: Special instructions round-trip
    @Property(tries = 100)
    void property5_specialInstructionsRoundTrip(
            @ForAll @StringLength(min = 0, max = 500) String specialInstructions
    ) {
        ShuttleBookingRequest request = ShuttleBookingRequest.builder()
                .pickupLocation("Hotel")
                .dropoffLocation("Airport")
                .pickupDatetime(LocalDateTime.now().plusDays(1))
                .passengerCount(2)
                .contactPhone("+1-555-0100")
                .specialInstructions(specialInstructions)
                .build();

        Reservation reservation = Reservation.builder().id(1L).build();
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(reservation));
        when(shuttleBookingRepository.countByReservationId(1L)).thenReturn(0L);
        when(shuttleBookingRepository.save(any(ShuttleBooking.class))).thenAnswer(invocation -> {
            ShuttleBooking b = invocation.getArgument(0);
            b.setId(1L);
            return b;
        });

        ShuttleBooking result = service.createBooking(1L, request);

        assertThat(result.getSpecialInstructions()).isEqualTo(specialInstructions);
    }

    // =========================================================================
    // Property 9: Valid status transitions succeed
    // =========================================================================
    // Feature: airport-shuttle-service, Property 9: Valid status transitions succeed
    @Example
    void property9_validStatusTransitionsSucceed() {
        // Apply REQUESTED→CONFIRMED→IN_PROGRESS→COMPLETED in sequence

        ShuttleBooking booking = ShuttleBooking.builder()
                .id(1L)
                .reservation(Reservation.builder().id(1L).build())
                .bookingReference("SHU-1-001")
                .status(BookingStatus.REQUESTED)
                .build();

        when(shuttleBookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(shuttleBookingRepository.save(any(ShuttleBooking.class))).thenAnswer(inv -> inv.getArgument(0));

        // Step 1: REQUESTED → CONFIRMED
        ShuttleBooking afterConfirm = service.updateStatus(1L, BookingStatus.CONFIRMED);
        assertThat(afterConfirm.getStatus()).isEqualTo(BookingStatus.CONFIRMED);

        // Step 2: CONFIRMED → IN_PROGRESS
        booking.setStatus(BookingStatus.CONFIRMED);
        ShuttleBooking afterInProgress = service.updateStatus(1L, BookingStatus.IN_PROGRESS);
        assertThat(afterInProgress.getStatus()).isEqualTo(BookingStatus.IN_PROGRESS);

        // Step 3: IN_PROGRESS → COMPLETED
        booking.setStatus(BookingStatus.IN_PROGRESS);
        ShuttleBooking afterCompleted = service.updateStatus(1L, BookingStatus.COMPLETED);
        assertThat(afterCompleted.getStatus()).isEqualTo(BookingStatus.COMPLETED);
    }

    // =========================================================================
    // Property 10: Invalid state transitions are rejected
    // =========================================================================
    // Feature: airport-shuttle-service, Property 10: Invalid state transitions are rejected
    @Property(tries = 50)
    void property10_invalidStatusTransitions_rejected(
            @ForAll("nonCancellableStatuses") BookingStatus nonCancellableStatus
    ) {
        ShuttleBooking booking = ShuttleBooking.builder()
                .id(1L)
                .reservation(Reservation.builder().id(1L).build())
                .bookingReference("SHU-1-001")
                .status(nonCancellableStatus)
                .build();

        when(shuttleBookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.updateStatus(1L, BookingStatus.CANCELLED))
                .isInstanceOf(InvalidStatusTransitionException.class);

        // Status must remain unchanged
        assertThat(booking.getStatus()).isEqualTo(nonCancellableStatus);
    }

    @Provide
    Arbitrary<BookingStatus> nonCancellableStatuses() {
        return Arbitraries.of(BookingStatus.COMPLETED, BookingStatus.IN_PROGRESS);
    }

    // =========================================================================
    // Property 11: Eligible cancellations transition to CANCELLED
    // =========================================================================
    // Feature: airport-shuttle-service, Property 11: Eligible cancellations transition to CANCELLED
    @Property(tries = 50)
    void property11_eligibleCancellations_setCancelled(
            @ForAll("cancellableStatuses") BookingStatus cancellableStatus
    ) {
        Reservation reservation = Reservation.builder().id(42L).build();
        ShuttleBooking booking = ShuttleBooking.builder()
                .id(1L)
                .reservation(reservation)
                .bookingReference("SHU-42-001")
                .status(cancellableStatus)
                .build();

        when(shuttleBookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(shuttleBookingRepository.save(any(ShuttleBooking.class))).thenAnswer(inv -> inv.getArgument(0));

        ShuttleBooking result = service.cancelBooking(1L, 42L);

        assertThat(result.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Provide
    Arbitrary<BookingStatus> cancellableStatuses() {
        return Arbitraries.of(BookingStatus.REQUESTED, BookingStatus.CONFIRMED);
    }

    // =========================================================================
    // Property 12: Ownership mismatch produces access denied
    // =========================================================================
    // Feature: airport-shuttle-service, Property 12: Ownership mismatch produces access denied
    @Property(tries = 100)
    void property12_ownershipMismatch_producesAccessDenied(
            @ForAll @IntRange(min = 1, max = 500) int reservationAId,
            @ForAll @IntRange(min = 501, max = 1000) int reservationBId
    ) {
        // reservationAId != reservationBId is guaranteed by the distinct ranges

        Reservation reservationA = Reservation.builder().id((long) reservationAId).build();
        ShuttleBooking booking = ShuttleBooking.builder()
                .id(1L)
                .reservation(reservationA)
                .bookingReference("SHU-" + reservationAId + "-001")
                .status(BookingStatus.REQUESTED)
                .build();

        when(shuttleBookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.cancelBooking(1L, (long) reservationBId))
                .isInstanceOf(ShuttleBookingAccessDeniedException.class);

        // Status must remain unchanged
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.REQUESTED);
    }
}
