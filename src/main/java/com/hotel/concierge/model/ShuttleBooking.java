package com.hotel.concierge.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "shuttle_bookings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShuttleBooking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Reservation reservation;

    @Column(name = "booking_reference", nullable = false, unique = true, length = 50)
    private String bookingReference;

    @Column(name = "pickup_location", nullable = false, length = 500)
    private String pickupLocation;

    @Column(name = "dropoff_location", nullable = false, length = 500)
    private String dropoffLocation;

    @Column(name = "pickup_datetime", nullable = false)
    private LocalDateTime pickupDatetime;

    @Column(name = "passenger_count", nullable = false)
    private Integer passengerCount;

    @Column(name = "contact_phone", nullable = false, length = 30)
    private String contactPhone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private BookingStatus status = BookingStatus.REQUESTED;

    @Column(name = "special_instructions", length = 1000)
    private String specialInstructions;

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

    public enum BookingStatus {
        REQUESTED, CONFIRMED, IN_PROGRESS, COMPLETED, CANCELLED
    }
}
