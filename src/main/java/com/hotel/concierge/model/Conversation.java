package com.hotel.concierge.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "conversations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, unique = true, length = 100)
    private String sessionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Reservation reservation;

    @Column(length = 20)
    @Builder.Default
    private String channel = "WEB";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ConversationStatus status = ConversationStatus.ACTIVE;

    @Column(name = "sentiment_score")
    private Double sentimentScore;

    @Column(name = "sentiment_label", length = 30)
    private String sentimentLabel;

    @Column(name = "is_escalated")
    @Builder.Default
    private Boolean isEscalated = false;

    @Column(name = "escalation_reason", length = 500)
    private String escalationReason;

    @Column(name = "assigned_agent", length = 100)
    private String assignedAgent;

    @Column(name = "language", length = 10)
    @Builder.Default
    private String language = "en";

    @Column(name = "message_count")
    @Builder.Default
    private Integer messageCount = 0;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        startedAt = LocalDateTime.now();
    }

    public enum ConversationStatus {
        ACTIVE, ESCALATED, RESOLVED, CLOSED
    }
}
