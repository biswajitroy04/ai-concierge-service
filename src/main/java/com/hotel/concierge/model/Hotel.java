package com.hotel.concierge.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "hotels")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Hotel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 500)
    private String address;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String country;

    @Column(length = 20)
    private String phone;

    @Column(length = 200)
    private String email;

    @Column(name = "star_rating")
    private Integer starRating;

    @Column(length = 50)
    private String timezone;

    @Column(name = "check_in_time", length = 10)
    private String checkInTime;

    @Column(name = "check_out_time", length = 10)
    private String checkOutTime;

    @Column(name = "late_checkout_fee")
    private Double lateCheckoutFee;

    @Column(name = "max_late_checkout_hour")
    private Integer maxLateCheckoutHour;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

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
}
