-- AI Concierge Hotel Platform - Seed Data
USE hotel_concierge;

-- Insert Hotels
INSERT INTO hotels (name, address, city, country, phone, email, star_rating, timezone, check_in_time, check_out_time, late_checkout_fee, max_late_checkout_hour) VALUES
('The Grand Meridian', '1 Luxury Avenue, Manhattan', 'New York', 'USA', '+1-212-555-0100', 'info@grandmeridian.com', 5, 'America/New_York', '15:00', '11:00', 75.00, 14),
('Azure Palace Resort', '500 Ocean Drive', 'Miami', 'USA', '+1-305-555-0200', 'info@azurepalace.com', 5, 'America/New_York', '16:00', '12:00', 50.00, 15),
('Imperial Sakura Hotel', '2-1 Ginza, Chuo-ku', 'Tokyo', 'Japan', '+81-3-5555-0300', 'info@imperialsakura.jp', 5, 'Asia/Tokyo', '15:00', '11:00', 8000.00, 14);

-- Insert Guests
INSERT INTO guests (first_name, last_name, email, phone, title, preferred_language, nationality, loyalty_tier, loyalty_points, total_stays, preferences, dietary_restrictions, special_occasions) VALUES
('Arjun', 'Roy', 'arjun.roy@email.com', '+1-555-0101', 'Mr.', 'en', 'Indian', 'PLATINUM', 45000, 12, 'High floor, city view, extra pillows', 'Vegetarian', 'Anniversary on May 22'),
('Sarah', 'Mitchell', 'sarah.m@email.com', '+1-555-0102', 'Ms.', 'en', 'American', 'GOLD', 28000, 8, 'Quiet room, hypoallergenic bedding', 'Gluten-free', NULL),
('Takeshi', 'Yamamoto', 'takeshi.y@email.com', '+81-90-5555-0103', 'Mr.', 'ja', 'Japanese', 'SILVER', 12000, 4, 'Japanese newspaper, green tea', NULL, NULL),
('Elena', 'Petrova', 'elena.p@email.com', '+7-555-0104', 'Mrs.', 'ru', 'Russian', 'PLATINUM', 62000, 18, 'Suite upgrade when available, champagne on arrival', NULL, 'Birthday on May 25'),
('James', 'Chen', 'james.chen@email.com', '+1-555-0105', 'Mr.', 'en', 'Canadian', NULL, 5000, 2, 'Late checkout preferred', 'Nut allergy', NULL);

-- Insert Reservations
INSERT INTO reservations (confirmation_number, hotel_id, guest_id, room_number, room_type, floor_number, check_in_date, check_out_date, number_of_guests, rate_per_night, currency, status, special_requests, is_vip) VALUES
('GM-2024-001', 1, 1, '1708', 'Executive Suite', 17, '2026-05-20', '2026-05-24', 2, 850.00, 'USD', 'CHECKED_IN', 'Extra pillows, city view confirmed', TRUE),
('GM-2024-002', 1, 2, '1205', 'Deluxe King', 12, '2026-05-19', '2026-05-23', 1, 550.00, 'USD', 'CHECKED_IN', 'Hypoallergenic bedding', FALSE),
('GM-2024-003', 1, 5, '0803', 'Superior Double', 8, '2026-05-20', '2026-05-22', 2, 420.00, 'USD', 'CHECKED_IN', NULL, FALSE),
('AP-2024-001', 2, 4, '2201', 'Presidential Suite', 22, '2026-05-18', '2026-05-26', 2, 2200.00, 'USD', 'CHECKED_IN', 'Champagne and roses on arrival', TRUE),
('IS-2024-001', 3, 3, '1501', 'Imperial Suite', 15, '2026-05-20', '2026-05-25', 1, 180000.00, 'JPY', 'CONFIRMED', 'Japanese newspaper daily', FALSE);

-- Insert App Users (password is 'admin123' bcrypt encoded)
INSERT INTO app_users (username, password, email, full_name, role, hotel_id) VALUES
('admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'admin@grandmeridian.com', 'System Administrator', 'ADMIN', 1),
('manager.smith', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'smith@grandmeridian.com', 'John Smith', 'MANAGER', 1),
('frontdesk.jones', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'jones@grandmeridian.com', 'Emily Jones', 'FRONT_DESK', 1),
('concierge.wilson', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'wilson@grandmeridian.com', 'David Wilson', 'CONCIERGE', 1);
