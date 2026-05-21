package com.hotel.concierge.repository;

import com.hotel.concierge.model.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findBySessionId(String sessionId);

    @Query("SELECT c FROM Conversation c WHERE c.status = 'ACTIVE' AND c.reservation.hotel.id = :hotelId")
    List<Conversation> findActiveByHotel(@Param("hotelId") Long hotelId);

    @Query("SELECT c FROM Conversation c WHERE c.reservation.hotel.id = :hotelId AND c.sentimentLabel IS NOT NULL")
    List<Conversation> findAllWithSentimentByHotel(@Param("hotelId") Long hotelId);

    @Query("SELECT COUNT(c) FROM Conversation c WHERE c.status = 'ACTIVE' AND c.reservation.hotel.id = :hotelId")
    long countActiveByHotel(@Param("hotelId") Long hotelId);

    @Query("SELECT COUNT(c) FROM Conversation c WHERE c.createdAt >= :since AND c.reservation.hotel.id = :hotelId")
    long countConversationsSince(@Param("hotelId") Long hotelId, @Param("since") LocalDateTime since);

    @Query("SELECT AVG(c.sentimentScore) FROM Conversation c WHERE c.sentimentScore IS NOT NULL AND c.sentimentScore != 0.0 AND c.reservation.hotel.id = :hotelId")
    Double averageSentimentByHotel(@Param("hotelId") Long hotelId);
}
