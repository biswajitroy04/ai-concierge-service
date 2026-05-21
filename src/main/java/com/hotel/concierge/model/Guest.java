package com.hotel.concierge.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "guests")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Guest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(length = 200)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(length = 10)
    private String title;

    @Column(name = "preferred_language", length = 10)
    @Builder.Default
    private String preferredLanguage = "en";

    @Column(length = 100)
    private String nationality;

    @Column(name = "loyalty_tier", length = 50)
    private String loyaltyTier;

    @Column(name = "loyalty_points")
    @Builder.Default
    private Integer loyaltyPoints = 0;

    @Column(name = "total_stays")
    @Builder.Default
    private Integer totalStays = 0;

    @Column(columnDefinition = "TEXT")
    private String preferences;

    @Column(name = "dietary_restrictions", length = 500)
    private String dietaryRestrictions;

    @Column(name = "special_occasions", length = 500)
    private String specialOccasions;

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

    public String getFullName() {
        return (title != null ? title + " " : "") + firstName + " " + lastName;
    }
}
