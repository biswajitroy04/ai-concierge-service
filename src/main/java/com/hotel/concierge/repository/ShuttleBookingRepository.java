package com.hotel.concierge.repository;

import com.hotel.concierge.model.ShuttleBooking;
import com.hotel.concierge.model.ShuttleBooking.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ShuttleBookingRepository extends JpaRepository<ShuttleBooking, Long> {

    List<ShuttleBooking> findByReservationIdOrderByPickupDatetimeAsc(Long reservationId);

    long countByReservationId(Long reservationId);

    Optional<ShuttleBooking> findByBookingReference(String bookingReference);

    @Query("SELECT s FROM ShuttleBooking s " +
           "WHERE s.reservation.hotel.id = :hotelId " +
           "AND (:status IS NULL OR s.status = :status) " +
           "AND (:pickupDate IS NULL OR FUNCTION('DATE', s.pickupDatetime) = :pickupDate)")
    List<ShuttleBooking> findByHotelWithFilters(
            @Param("hotelId") Long hotelId,
            @Param("status") BookingStatus status,
            @Param("pickupDate") LocalDate pickupDate);
}
