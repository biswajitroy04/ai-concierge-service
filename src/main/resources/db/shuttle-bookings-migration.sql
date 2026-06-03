-- ============================================================
-- Airport Shuttle Service: Add shuttle_bookings table
-- Run after schema.sql (or add at end of schema.sql for fresh installs)
-- MySQL 8.0+
-- ============================================================

USE hotel_concierge;

CREATE TABLE IF NOT EXISTS shuttle_bookings (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    reservation_id       BIGINT NOT NULL,
    booking_reference    VARCHAR(50) NOT NULL,
    pickup_location      VARCHAR(500) NOT NULL,
    dropoff_location     VARCHAR(500) NOT NULL,
    pickup_datetime      DATETIME NOT NULL,
    passenger_count      INT NOT NULL,
    contact_phone        VARCHAR(30) NOT NULL,
    status               VARCHAR(30) NOT NULL DEFAULT 'REQUESTED',
    special_instructions VARCHAR(1000),
    created_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_shuttle_reservation FOREIGN KEY (reservation_id) REFERENCES reservations(id),
    CONSTRAINT uq_shuttle_reference   UNIQUE (booking_reference),
    CONSTRAINT chk_passenger_count    CHECK (passenger_count >= 1),
    INDEX idx_shuttle_reservation (reservation_id),
    INDEX idx_shuttle_status      (status),
    INDEX idx_shuttle_pickup_date (pickup_datetime)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
