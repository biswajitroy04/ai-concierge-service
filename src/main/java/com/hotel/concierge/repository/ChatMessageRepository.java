package com.hotel.concierge.repository;

import com.hotel.concierge.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    @Query("SELECT m FROM ChatMessage m WHERE m.conversation.sessionId = :sessionId ORDER BY m.createdAt ASC")
    List<ChatMessage> findBySessionIdOrdered(@Param("sessionId") String sessionId);

    @Query("SELECT m FROM ChatMessage m WHERE m.conversation.id = :conversationId ORDER BY m.createdAt DESC")
    List<ChatMessage> findRecentByConversation(@Param("conversationId") Long conversationId);
}
