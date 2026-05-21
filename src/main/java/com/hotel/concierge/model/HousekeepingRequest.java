package com.hotel.concierge.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "housekeeping_requests")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HousekeepingRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Reservation reservation;

    @Column(name = "request_type", nullable = false, length = 100)
    private String requestType;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private RequestPriority priority = RequestPriority.NORMAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private RequestStatus status = RequestStatus.PENDING;

    @Column(name = "assigned_staff", length = 100)
    private String assignedStaff;

    @Column(name = "estimated_completion_minutes")
    private Integer estimatedCompletionMinutes;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum RequestPriority {
        LOW, NORMAL, HIGH, URGENT
    }

    public enum RequestStatus {
        PENDING, ASSIGNED, IN_PROGRESS, COMPLETED, CANCELLED
    }
}
