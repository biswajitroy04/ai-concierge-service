package com.hotel.concierge.repository;

import com.hotel.concierge.model.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    Optional<Reservation> findByConfirmationNumber(String confirmationNumber);

    @Query("SELECT r FROM Reservation r WHERE r.guest.id = :guestId AND r.status IN ('CONFIRMED', 'CHECKED_IN') ORDER BY r.checkInDate DESC")
    List<Reservation> findActiveByGuestId(@Param("guestId") Long guestId);

    @Query("SELECT r FROM Reservation r WHERE r.hotel.id = :hotelId AND r.checkInDate <= :date AND r.checkOutDate >= :date AND r.status = 'CHECKED_IN'")
    List<Reservation> findCurrentGuestsByHotel(@Param("hotelId") Long hotelId, @Param("date") LocalDate date);

    @Query("SELECT r FROM Reservation r WHERE r.hotel.id = :hotelId AND r.status = 'CHECKED_IN'")
    List<Reservation> findCheckedInByHotel(@Param("hotelId") Long hotelId);

    @Query("SELECT COUNT(r) FROM Reservation r WHERE r.hotel.id = :hotelId AND r.checkOutDate = :date AND r.lateCheckoutApproved = false")
    long countEligibleForLateCheckout(@Param("hotelId") Long hotelId, @Param("date") LocalDate date);
}
