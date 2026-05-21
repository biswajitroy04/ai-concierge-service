package com.hotel.concierge.repository;

import com.hotel.concierge.model.SpaBooking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface SpaBookingRepository extends JpaRepository<SpaBooking, Long> {

    List<SpaBooking> findByReservationId(Long reservationId);

    @Query("SELECT s FROM SpaBooking s WHERE s.bookingDate = :date AND s.status = 'CONFIRMED' AND s.reservation.hotel.id = :hotelId")
    List<SpaBooking> findConfirmedByDateAndHotel(@Param("date") LocalDate date, @Param("hotelId") Long hotelId);
}
