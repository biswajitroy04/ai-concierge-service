package com.hotel.concierge.service;

import com.hotel.concierge.dto.ShuttleBookingRequest;
import com.hotel.concierge.exception.InvalidStatusTransitionException;
import com.hotel.concierge.exception.ShuttleBookingAccessDeniedException;
import com.hotel.concierge.exception.ShuttleBookingNotFoundException;
import com.hotel.concierge.model.Reservation;
import com.hotel.concierge.model.ShuttleBooking;
import com.hotel.concierge.model.ShuttleBooking.BookingStatus;
import com.hotel.concierge.repository.ReservationRepository;
import com.hotel.concierge.repository.ShuttleBookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ShuttleBookingService} covering all service methods
 * with concrete examples.
 */
@ExtendWith(MockitoExtension.class)
class ShuttleBookingServiceTest {

    @Mock
    private ShuttleBookingRepository shuttleBookingRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @InjectMocks
    private ShuttleBookingService service;

    private Reservation reservation;

    @BeforeEach
    void setUp() {
        reservation = Reservation.builder()
                .id(42L)
                .confirmationNumber("CONF-42")
                .build();
    }

    // -------------------------------------------------------------------------
    // createBooking — happy path
    // -------------------------------------------------------------------------

    @Test
    void createBooking_happyPath_returnsRequestedStatus() {
        ShuttleBookingRequest request = ShuttleBookingRequest.builder()
                .pickupLocation("Hotel Main Entrance")
                .dropoffLocation("JFK Terminal 4")
                .pickupDatetime(LocalDateTime.now().plusDays(1))
                .passengerCount(2)
                .contactPhone("+1-555-0100")
                .specialInstructions("Large luggage")
                .build();

        when(reservationRepository.findById(42L)).thenReturn(Optional.of(reservation));
        when(shuttleBookingRepository.countByReservationId(42L)).thenReturn(0L);

        ShuttleBooking saved = ShuttleBooking.builder()
                .id(1L)
                .reservation(reservation)
                .bookingReference("SHU-42-001")
                .pickupLocation(request.getPickupLocation())
                .dropoffLocation(request.getDropoffLocation())
                .pickupDatetime(request.getPickupDatetime())
                .passengerCount(request.getPassengerCount())
                .contactPhone(request.getContactPhone())
                .specialInstructions(request.getSpecialInstructions())
                .status(BookingStatus.REQUESTED)
                .build();
        when(shuttleBookingRepository.save(any(ShuttleBooking.class))).thenReturn(saved);

        ShuttleBooking result = service.createBooking(42L, request);

        assertThat(result.getStatus()).isEqualTo(BookingStatus.REQUESTED);
        assertThat(result.getPickupLocation()).isEqualTo("Hotel Main Entrance");
        assertThat(result.getDropoffLocation()).isEqualTo("JFK Terminal 4");
        assertThat(result.getPassengerCount()).isEqualTo(2);
        assertThat(result.getContactPhone()).isEqualTo("+1-555-0100");
        assertThat(result.getSpecialInstructions()).isEqualTo("Large luggage");
        assertThat(result.getBookingReference()).isEqualTo("SHU-42-001");
    }

    // -------------------------------------------------------------------------
    // createBooking — past datetime
    // -------------------------------------------------------------------------

    @Test
    void createBooking_pastDatetime_throwsIllegalArgumentException() {
        ShuttleBookingRequest request = ShuttleBookingRequest.builder()
                .pickupLocation("Hotel")
                .dropoffLocation("Airport")
                .pickupDatetime(LocalDateTime.now().minusHours(1))
                .passengerCount(2)
                .contactPhone("+1-555-0100")
                .build();

        when(reservationRepository.findById(42L)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> service.createBooking(42L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("future");

        verify(shuttleBookingRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // createBooking — passengerCount = 0
    // -------------------------------------------------------------------------

    @Test
    void createBooking_passengerCountZero_throwsIllegalArgumentException() {
        ShuttleBookingRequest request = ShuttleBookingRequest.builder()
                .pickupLocation("Hotel")
                .dropoffLocation("Airport")
                .pickupDatetime(LocalDateTime.now().plusDays(1))
                .passengerCount(0)
                .contactPhone("+1-555-0100")
                .build();

        when(reservationRepository.findById(42L)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> service.createBooking(42L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1");

        verify(shuttleBookingRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // createBooking — passengerCount = 11
    // -------------------------------------------------------------------------

    @Test
    void createBooking_passengerCountEleven_throwsIllegalArgumentException() {
        ShuttleBookingRequest request = ShuttleBookingRequest.builder()
                .pickupLocation("Hotel")
                .dropoffLocation("Airport")
                .pickupDatetime(LocalDateTime.now().plusDays(1))
                .passengerCount(11)
                .contactPhone("+1-555-0100")
                .build();

        when(reservationRepository.findById(42L)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> service.createBooking(42L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10");

        verify(shuttleBookingRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // getBookingById — not found
    // -------------------------------------------------------------------------

    @Test
    void getBookingById_notFound_throwsNotFoundException() {
        when(shuttleBookingRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getBookingById(99L))
                .isInstanceOf(ShuttleBookingNotFoundException.class)
                .hasMessageContaining("99");
    }

    // -------------------------------------------------------------------------
    // updateStatus — REQUESTED → CONFIRMED (valid transition)
    // -------------------------------------------------------------------------

    @Test
    void updateStatus_requestedToConfirmed_succeeds() {
        ShuttleBooking booking = ShuttleBooking.builder()
                .id(1L)
                .reservation(reservation)
                .bookingReference("SHU-42-001")
                .status(BookingStatus.REQUESTED)
                .build();

        ShuttleBooking updated = ShuttleBooking.builder()
                .id(1L)
                .reservation(reservation)
                .bookingReference("SHU-42-001")
                .status(BookingStatus.CONFIRMED)
                .build();

        when(shuttleBookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(shuttleBookingRepository.save(any(ShuttleBooking.class))).thenReturn(updated);

        ShuttleBooking result = service.updateStatus(1L, BookingStatus.CONFIRMED);

        assertThat(result.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    // -------------------------------------------------------------------------
    // updateStatus — IN_PROGRESS → CANCELLED (invalid transition)
    // -------------------------------------------------------------------------

    @Test
    void updateStatus_inProgressToCancelled_throwsInvalidTransition() {
        ShuttleBooking booking = ShuttleBooking.builder()
                .id(1L)
                .reservation(reservation)
                .bookingReference("SHU-42-001")
                .status(BookingStatus.IN_PROGRESS)
                .build();

        when(shuttleBookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.updateStatus(1L, BookingStatus.CANCELLED))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    // -------------------------------------------------------------------------
    // updateStatus — COMPLETED → any (terminal state)
    // -------------------------------------------------------------------------

    @Test
    void updateStatus_completedToAny_throwsInvalidTransition() {
        ShuttleBooking booking = ShuttleBooking.builder()
                .id(1L)
                .reservation(reservation)
                .bookingReference("SHU-42-001")
                .status(BookingStatus.COMPLETED)
                .build();

        when(shuttleBookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.updateStatus(1L, BookingStatus.CANCELLED))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    // -------------------------------------------------------------------------
    // cancelBooking — REQUESTED with matching reservationId
    // -------------------------------------------------------------------------

    @Test
    void cancelBooking_requestedStatus_matchingReservation_setsCancelled() {
        ShuttleBooking booking = ShuttleBooking.builder()
                .id(1L)
                .reservation(reservation)
                .bookingReference("SHU-42-001")
                .status(BookingStatus.REQUESTED)
                .build();

        ShuttleBooking cancelled = ShuttleBooking.builder()
                .id(1L)
                .reservation(reservation)
                .bookingReference("SHU-42-001")
                .status(BookingStatus.CANCELLED)
                .build();

        when(shuttleBookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(shuttleBookingRepository.save(any(ShuttleBooking.class))).thenReturn(cancelled);

        ShuttleBooking result = service.cancelBooking(1L, 42L);

        assertThat(result.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    // -------------------------------------------------------------------------
    // cancelBooking — CONFIRMED with matching reservationId
    // -------------------------------------------------------------------------

    @Test
    void cancelBooking_confirmedStatus_matchingReservation_setsCancelled() {
        ShuttleBooking booking = ShuttleBooking.builder()
                .id(1L)
                .reservation(reservation)
                .bookingReference("SHU-42-001")
                .status(BookingStatus.CONFIRMED)
                .build();

        ShuttleBooking cancelled = ShuttleBooking.builder()
                .id(1L)
                .reservation(reservation)
                .bookingReference("SHU-42-001")
                .status(BookingStatus.CANCELLED)
                .build();

        when(shuttleBookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(shuttleBookingRepository.save(any(ShuttleBooking.class))).thenReturn(cancelled);

        ShuttleBooking result = service.cancelBooking(1L, 42L);

        assertThat(result.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    // -------------------------------------------------------------------------
    // cancelBooking — IN_PROGRESS → invalid
    // -------------------------------------------------------------------------

    @Test
    void cancelBooking_inProgressStatus_throwsInvalidTransition() {
        ShuttleBooking booking = ShuttleBooking.builder()
                .id(1L)
                .reservation(reservation)
                .bookingReference("SHU-42-001")
                .status(BookingStatus.IN_PROGRESS)
                .build();

        when(shuttleBookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.cancelBooking(1L, 42L))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("IN_PROGRESS");
    }

    // -------------------------------------------------------------------------
    // cancelBooking — wrong reservationId → access denied
    // -------------------------------------------------------------------------

    @Test
    void cancelBooking_wrongReservationId_throwsAccessDeniedException() {
        ShuttleBooking booking = ShuttleBooking.builder()
                .id(1L)
                .reservation(reservation)  // reservation.id = 42
                .bookingReference("SHU-42-001")
                .status(BookingStatus.REQUESTED)
                .build();

        when(shuttleBookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        // Cancel with a different reservation ID (99 != 42)
        assertThatThrownBy(() -> service.cancelBooking(1L, 99L))
                .isInstanceOf(ShuttleBookingAccessDeniedException.class);
    }

    // -------------------------------------------------------------------------
    // getBookingsByReservation — returns list in order
    // -------------------------------------------------------------------------

    @Test
    void getBookingsByReservation_returnsListInAscendingOrder() {
        LocalDateTime t1 = LocalDateTime.now().plusDays(1);
        LocalDateTime t2 = LocalDateTime.now().plusDays(2);

        ShuttleBooking b1 = ShuttleBooking.builder().id(1L).reservation(reservation)
                .pickupDatetime(t1).status(BookingStatus.REQUESTED).build();
        ShuttleBooking b2 = ShuttleBooking.builder().id(2L).reservation(reservation)
                .pickupDatetime(t2).status(BookingStatus.REQUESTED).build();

        when(shuttleBookingRepository.findByReservationIdOrderByPickupDatetimeAsc(42L))
                .thenReturn(List.of(b1, b2));

        List<ShuttleBooking> result = service.getBookingsByReservation(42L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getPickupDatetime()).isEqualTo(t1);
        assertThat(result.get(1).getPickupDatetime()).isEqualTo(t2);
    }

    // -------------------------------------------------------------------------
    // getBookingsByHotel — delegates to repository
    // -------------------------------------------------------------------------

    @Test
    void getBookingsByHotel_delegatesToRepository() {
        ShuttleBooking b = ShuttleBooking.builder().id(1L).reservation(reservation)
                .status(BookingStatus.CONFIRMED).build();

        when(shuttleBookingRepository.findByHotelWithFilters(eq(10L), eq(BookingStatus.CONFIRMED), eq(null)))
                .thenReturn(List.of(b));

        List<ShuttleBooking> result = service.getBookingsByHotel(10L, BookingStatus.CONFIRMED, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(shuttleBookingRepository).findByHotelWithFilters(10L, BookingStatus.CONFIRMED, null);
    }
}
