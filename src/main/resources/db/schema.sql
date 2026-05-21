-- ============================================================
-- AI Concierge Hotel Platform - MySQL Database Schema
-- Run this script to create all required tables
-- MySQL 8.0+
-- ============================================================

CREATE DATABASE IF NOT EXISTS hotel_concierge
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE hotel_concierge;

-- Drop tables in reverse dependency order (for re-runs)
DROP TABLE IF EXISTS audit_logs;
DROP TABLE IF EXISTS escalation_tickets;
DROP TABLE IF EXISTS chat_messages;
DROP TABLE IF EXISTS conversations;
DROP TABLE IF EXISTS restaurant_bookings;
DROP TABLE IF EXISTS spa_bookings;
DROP TABLE IF EXISTS housekeeping_requests;
DROP TABLE IF EXISTS reservations;
DROP TABLE IF EXISTS app_users;
DROP TABLE IF EXISTS guests;
DROP TABLE IF EXISTS hotels;

-- ============================================================
-- 1. HOTELS
-- ============================================================
CREATE TABLE hotels (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    address VARCHAR(500) NOT NULL,
    city VARCHAR(100),
    country VARCHAR(100),
    phone VARCHAR(20),
    email VARCHAR(200),
    star_rating INT,
    timezone VARCHAR(50) DEFAULT 'UTC',
    check_in_time VARCHAR(10) DEFAULT '15:00',
    check_out_time VARCHAR(10) DEFAULT '11:00',
    late_checkout_fee DOUBLE DEFAULT 50.0,
    max_late_checkout_hour INT DEFAULT 14,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- ============================================================
-- 2. GUESTS
-- ============================================================
CREATE TABLE guests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(200),
    phone VARCHAR(20),
    title VARCHAR(10),
    preferred_language VARCHAR(10) DEFAULT 'en',
    nationality VARCHAR(100),
    loyalty_tier VARCHAR(50),
    loyalty_points INT DEFAULT 0,
    total_stays INT DEFAULT 0,
    preferences TEXT,
    dietary_restrictions VARCHAR(500),
    special_occasions VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_guest_email (email),
    INDEX idx_guest_phone (phone)
) ENGINE=InnoDB;

-- ============================================================
-- 3. APP USERS (Hotel Staff / Admin)
-- ============================================================
CREATE TABLE app_users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(200) NOT NULL,
    full_name VARCHAR(200),
    role VARCHAR(30) NOT NULL,
    hotel_id BIGINT,
    active BOOLEAN DEFAULT TRUE,
    last_login TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (hotel_id) REFERENCES hotels(id),
    INDEX idx_user_username (username)
) ENGINE=InnoDB;

-- ============================================================
-- 4. RESERVATIONS
-- ============================================================
CREATE TABLE reservations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    confirmation_number VARCHAR(50) NOT NULL UNIQUE,
    hotel_id BIGINT NOT NULL,
    guest_id BIGINT NOT NULL,
    room_number VARCHAR(20),
    room_type VARCHAR(100),
    floor_number INT,
    check_in_date DATE NOT NULL,
    check_out_date DATE NOT NULL,
    number_of_guests INT DEFAULT 1,
    rate_per_night DOUBLE,
    currency VARCHAR(10) DEFAULT 'USD',
    status VARCHAR(30) NOT NULL DEFAULT 'CONFIRMED',
    special_requests TEXT,
    is_vip BOOLEAN DEFAULT FALSE,
    late_checkout_approved BOOLEAN DEFAULT FALSE,
    late_checkout_time VARCHAR(10),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (hotel_id) REFERENCES hotels(id),
    FOREIGN KEY (guest_id) REFERENCES guests(id),
    INDEX idx_reservation_confirmation (confirmation_number),
    INDEX idx_reservation_hotel_status (hotel_id, status),
    INDEX idx_reservation_dates (check_in_date, check_out_date)
) ENGINE=InnoDB;

-- ============================================================
-- 5. HOUSEKEEPING REQUESTS
-- ============================================================
CREATE TABLE housekeeping_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    reservation_id BIGINT NOT NULL,
    request_type VARCHAR(100) NOT NULL,
    description TEXT,
    priority VARCHAR(30) NOT NULL DEFAULT 'NORMAL',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    assigned_staff VARCHAR(100),
    estimated_completion_minutes INT,
    completed_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (reservation_id) REFERENCES reservations(id),
    INDEX idx_housekeeping_status (status),
    INDEX idx_housekeeping_priority (priority)
) ENGINE=InnoDB;

-- ============================================================
-- 6. SPA BOOKINGS
-- ============================================================
CREATE TABLE spa_bookings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    reservation_id BIGINT NOT NULL,
    service_name VARCHAR(200) NOT NULL,
    therapist_name VARCHAR(100),
    booking_date DATE NOT NULL,
    start_time TIME NOT NULL,
    duration_minutes INT NOT NULL,
    price DOUBLE NOT NULL,
    currency VARCHAR(10) DEFAULT 'USD',
    status VARCHAR(30) NOT NULL DEFAULT 'CONFIRMED',
    special_notes VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (reservation_id) REFERENCES reservations(id),
    INDEX idx_spa_date (booking_date)
) ENGINE=InnoDB;

-- ============================================================
-- 7. RESTAURANT BOOKINGS
-- ============================================================
CREATE TABLE restaurant_bookings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    reservation_id BIGINT NOT NULL,
    restaurant_name VARCHAR(200) NOT NULL,
    booking_date DATE NOT NULL,
    booking_time TIME NOT NULL,
    party_size INT NOT NULL,
    seating_preference VARCHAR(100),
    special_requests VARCHAR(500),
    status VARCHAR(30) NOT NULL DEFAULT 'CONFIRMED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (reservation_id) REFERENCES reservations(id),
    INDEX idx_restaurant_date (booking_date)
) ENGINE=InnoDB;

-- ============================================================
-- 8. CONVERSATIONS
-- ============================================================
CREATE TABLE conversations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(100) NOT NULL UNIQUE,
    reservation_id BIGINT NOT NULL,
    channel VARCHAR(20) DEFAULT 'WEB',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    sentiment_score DOUBLE,
    sentiment_label VARCHAR(30),
    is_escalated BOOLEAN DEFAULT FALSE,
    escalation_reason VARCHAR(500),
    assigned_agent VARCHAR(100),
    language VARCHAR(10) DEFAULT 'en',
    message_count INT DEFAULT 0,
    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ended_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (reservation_id) REFERENCES reservations(id),
    INDEX idx_conversation_session (session_id),
    INDEX idx_conversation_status (status),
    INDEX idx_conversation_reservation (reservation_id)
) ENGINE=InnoDB;

-- ============================================================
-- 9. CHAT MESSAGES
-- ============================================================
CREATE TABLE chat_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    tool_call VARCHAR(200),
    tool_result TEXT,
    sentiment_score DOUBLE,
    tokens_used INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (conversation_id) REFERENCES conversations(id),
    INDEX idx_message_conversation (conversation_id),
    INDEX idx_message_created (created_at)
) ENGINE=InnoDB;

-- ============================================================
-- 10. ESCALATION TICKETS
-- ============================================================
CREATE TABLE escalation_tickets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    reservation_id BIGINT NOT NULL,
    reason VARCHAR(200) NOT NULL,
    description TEXT,
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    assigned_to VARCHAR(100),
    sentiment_score DOUBLE,
    resolution_notes TEXT,
    resolved_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (conversation_id) REFERENCES conversations(id),
    FOREIGN KEY (reservation_id) REFERENCES reservations(id),
    INDEX idx_escalation_status (status),
    INDEX idx_escalation_priority (priority)
) ENGINE=InnoDB;

-- ============================================================
-- 11. AUDIT LOGS
-- ============================================================
CREATE TABLE audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id BIGINT,
    user_id VARCHAR(100),
    user_role VARCHAR(50),
    ip_address VARCHAR(50),
    details TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_action (action),
    INDEX idx_audit_entity (entity_type, entity_id),
    INDEX idx_audit_created (created_at)
) ENGINE=InnoDB;

-- ============================================================
-- DONE: 11 tables created
-- ============================================================
-- Run order: hotels → guests → app_users → reservations →
--   housekeeping_requests → spa_bookings → restaurant_bookings →
--   conversations → chat_messages → escalation_tickets → audit_logs
-- ============================================================
