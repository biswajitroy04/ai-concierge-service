package com.hotel.concierge.repository;

import com.hotel.concierge.model.RestaurantBooking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface RestaurantBookingRepository extends JpaRepository<RestaurantBooking, Long> {
    List<RestaurantBooking> findByReservationId(Long reservationId);
}
