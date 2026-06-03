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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class ShuttleBookingService {

    private final ShuttleBookingRepository shuttleBookingRepository;
    private final ReservationRepository reservationRepository;

    // State machine: maps each status to the set of valid target statuses
    private static final Map<BookingStatus, Set<BookingStatus>> VALID_TRANSITIONS = Map.of(
            BookingStatus.REQUESTED,   EnumSet.of(BookingStatus.CONFIRMED, BookingStatus.CANCELLED),
            BookingStatus.CONFIRMED,   EnumSet.of(BookingStatus.IN_PROGRESS, BookingStatus.CANCELLED),
            BookingStatus.IN_PROGRESS, EnumSet.of(BookingStatus.COMPLETED),
            BookingStatus.COMPLETED,   EnumSet.noneOf(BookingStatus.class),
            BookingStatus.CANCELLED,   EnumSet.noneOf(BookingStatus.class)
    );

    /**
     * Create a new shuttle booking for the given reservation.
     *
     * @param reservationId the ID of the reservation to associate with this booking
     * @param request       the booking details supplied by the guest
     * @return the persisted {@link ShuttleBooking} with status {@code REQUESTED}
     */
    public ShuttleBooking createBooking(Long reservationId, ShuttleBookingRequest request) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reservation not found: " + reservationId));

        if (!request.getPickupDatetime().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Pickup date and time must be in the future");
        }

        if (request.getPassengerCount() == null
                || request.getPassengerCount() < 1
                || request.getPassengerCount() > 10) {
            throw new IllegalArgumentException("Passenger count must be between 1 and 10");
        }

        long count = shuttleBookingRepository.countByReservationId(reservationId);
        String bookingReference = String.format("SHU-%d-%03d", reservationId, count + 1);

        ShuttleBooking booking = ShuttleBooking.builder()
                .reservation(reservation)
                .bookingReference(bookingReference)
                .pickupLocation(request.getPickupLocation())
                .dropoffLocation(request.getDropoffLocation())
                .pickupDatetime(request.getPickupDatetime())
                .passengerCount(request.getPassengerCount())
                .contactPhone(request.getContactPhone())
                .specialInstructions(request.getSpecialInstructions())
                .status(BookingStatus.REQUESTED)
                .build();

        return shuttleBookingRepository.save(booking);
    }

    /**
     * Retrieve a single booking by its database ID.
     *
     * @param bookingId the booking ID
     * @return the {@link ShuttleBooking}
     * @throws ShuttleBookingNotFoundException if no booking exists with that ID
     */
    @Transactional(readOnly = true)
    public ShuttleBooking getBookingById(Long bookingId) {
        return shuttleBookingRepository.findById(bookingId)
                .orElseThrow(() -> new ShuttleBookingNotFoundException(
                        "Shuttle booking not found: " + bookingId));
    }

    /**
     * List all bookings for a reservation, ordered by pickup datetime ascending.
     *
     * @param reservationId the reservation ID
     * @return list of {@link ShuttleBooking} sorted by pickup datetime asc
     */
    @Transactional(readOnly = true)
    public List<ShuttleBooking> getBookingsByReservation(Long reservationId) {
        return shuttleBookingRepository.findByReservationIdOrderByPickupDatetimeAsc(reservationId);
    }

    /**
     * List bookings for a hotel with optional status and date filters.
     *
     * @param hotelId    the hotel ID
     * @param status     optional status filter (null = no filter)
     * @param pickupDate optional pickup date filter (null = no filter)
     * @return matching {@link ShuttleBooking} records
     */
    @Transactional(readOnly = true)
    public List<ShuttleBooking> getBookingsByHotel(Long hotelId, BookingStatus status, LocalDate pickupDate) {
        return shuttleBookingRepository.findByHotelWithFilters(hotelId, status, pickupDate);
    }

    /**
     * Transition a booking to a new status, enforcing the state machine.
     *
     * @param bookingId the booking ID
     * @param newStatus the desired target status
     * @return the updated and persisted {@link ShuttleBooking}
     * @throws ShuttleBookingNotFoundException    if no booking exists with that ID
     * @throws InvalidStatusTransitionException   if the transition is not allowed
     */
    public ShuttleBooking updateStatus(Long bookingId, BookingStatus newStatus) {
        ShuttleBooking booking = shuttleBookingRepository.findById(bookingId)
                .orElseThrow(() -> new ShuttleBookingNotFoundException(
                        "Shuttle booking not found: " + bookingId));

        BookingStatus current = booking.getStatus();
        Set<BookingStatus> allowed = VALID_TRANSITIONS.get(current);

        if (allowed == null || !allowed.contains(newStatus)) {
            throw new InvalidStatusTransitionException(
                    "Cannot transition from " + current + " to " + newStatus);
        }

        booking.setStatus(newStatus);
        return shuttleBookingRepository.save(booking);
    }

    /**
     * Cancel a booking, verifying ownership and eligible status.
     *
     * @param bookingId     the booking ID to cancel
     * @param reservationId the reservation ID of the requesting guest (ownership check)
     * @return the updated and persisted {@link ShuttleBooking} with status {@code CANCELLED}
     * @throws ShuttleBookingNotFoundException      if no booking exists with that ID
     * @throws ShuttleBookingAccessDeniedException  if the booking does not belong to the reservation
     * @throws InvalidStatusTransitionException     if the booking is not in a cancellable status
     */
    public ShuttleBooking cancelBooking(Long bookingId, Long reservationId) {
        ShuttleBooking booking = shuttleBookingRepository.findById(bookingId)
                .orElseThrow(() -> new ShuttleBookingNotFoundException(
                        "Shuttle booking not found: " + bookingId));

        if (!booking.getReservation().getId().equals(reservationId)) {
            throw new ShuttleBookingAccessDeniedException(
                    "You do not have permission to cancel this booking");
        }

        BookingStatus status = booking.getStatus();
        if (status != BookingStatus.REQUESTED && status != BookingStatus.CONFIRMED) {
            throw new InvalidStatusTransitionException(
                    "Cannot cancel a booking in status: " + status);
        }

        booking.setStatus(BookingStatus.CANCELLED);
        return shuttleBookingRepository.save(booking);
    }
}
