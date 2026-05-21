package com.hotel.concierge.repository;

import com.hotel.concierge.model.HousekeepingRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface HousekeepingRequestRepository extends JpaRepository<HousekeepingRequest, Long> {

    @Query("SELECT h FROM HousekeepingRequest h WHERE h.reservation.hotel.id = :hotelId AND h.status = 'PENDING' ORDER BY h.priority DESC, h.createdAt ASC")
    List<HousekeepingRequest> findPendingByHotel(@Param("hotelId") Long hotelId);

    @Query("SELECT COUNT(h) FROM HousekeepingRequest h WHERE h.reservation.hotel.id = :hotelId AND h.status = 'PENDING'")
    long countPendingByHotel(@Param("hotelId") Long hotelId);

    List<HousekeepingRequest> findByReservationId(Long reservationId);
}
