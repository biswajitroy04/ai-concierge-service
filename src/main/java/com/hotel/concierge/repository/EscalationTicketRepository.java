package com.hotel.concierge.repository;

import com.hotel.concierge.model.EscalationTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface EscalationTicketRepository extends JpaRepository<EscalationTicket, Long> {

    @Query("SELECT e FROM EscalationTicket e WHERE e.reservation.hotel.id = :hotelId AND e.status IN ('OPEN', 'IN_PROGRESS') ORDER BY e.priority DESC, e.createdAt ASC")
    List<EscalationTicket> findActiveByHotel(@Param("hotelId") Long hotelId);

    @Query("SELECT COUNT(e) FROM EscalationTicket e WHERE e.reservation.hotel.id = :hotelId AND e.status = 'OPEN'")
    long countOpenByHotel(@Param("hotelId") Long hotelId);
}
