package com.hotel.concierge.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;

@Entity
@Table(name = "restaurant_bookings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RestaurantBooking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Reservation reservation;

    @Column(name = "restaurant_name", nullable = false, length = 200)
    private String restaurantName;

    @Column(name = "booking_date", nullable = false)
    private LocalDate bookingDate;

    @Column(name = "booking_time", nullable = false)
    private LocalTime bookingTime;

    @Column(name = "party_size", nullable = false)
    private Integer partySize;

    @Column(name = "seating_preference", length = 100)
    private String seatingPreference;

    @Column(name = "special_requests", length = 500)
    private String specialRequests;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private BookingStatus status = BookingStatus.CONFIRMED;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum BookingStatus {
        CONFIRMED, SEATED, COMPLETED, CANCELLED, NO_SHOW
    }
}
