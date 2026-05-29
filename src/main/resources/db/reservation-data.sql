-- ============================================================
-- Additional Reservation Data for The Grand Meridian (hotel_id = 1)
-- Period: May 25, 2026 through June 30, 2026
-- Run AFTER seed-data.sql
-- ============================================================



USE hotel_concierge;

INSERT INTO reservations (confirmation_number, hotel_id, guest_id, room_number, room_type, floor_number, check_in_date, check_out_date, number_of_guests, rate_per_night, currency, status, special_requests, is_vip) VALUES

-- Week of May 25 - May 31
('GM-2026-006', 1, 3, '1402', 'Deluxe King', 14, '2026-05-25', '2026-05-28', 1, 550.00, 'USD', 'CONFIRMED', 'Green tea in room, Japanese newspaper', FALSE),
('GM-2026-007', 1, 4, '1801', 'Penthouse Suite', 18, '2026-05-26', '2026-05-30', 2, 1500.00, 'USD', 'CONFIRMED', 'Champagne on arrival, birthday celebration', TRUE),
('GM-2026-008', 1, 5, '0905', 'Superior Double', 9, '2026-05-27', '2026-05-29', 2, 420.00, 'USD', 'CONFIRMED', NULL, FALSE),
('GM-2026-009', 1, 2, '1306', 'Deluxe King', 13, '2026-05-28', '2026-06-01', 1, 550.00, 'USD', 'CONFIRMED', 'Hypoallergenic bedding, quiet room', FALSE),
('GM-2026-010', 1, 1, '1710', 'Executive Suite', 17, '2026-05-29', '2026-06-02', 2, 850.00, 'USD', 'CONFIRMED', 'Extra pillows, city view, vegetarian meals', TRUE),
('GM-2026-011', 1, 3, '0812', 'Standard King', 8, '2026-05-30', '2026-06-01', 1, 380.00, 'USD', 'CONFIRMED', 'Green tea', FALSE),
('GM-2026-012', 1, 5, '1104', 'Deluxe Twin', 11, '2026-05-31', '2026-06-03', 2, 480.00, 'USD', 'CONFIRMED', 'Late checkout preferred', FALSE),

-- Week of June 1 - June 7
('GM-2026-013', 1, 2, '1506', 'Superior King', 15, '2026-06-01', '2026-06-04', 1, 480.00, 'USD', 'CONFIRMED', 'Hypoallergenic bedding, gluten-free breakfast', FALSE),
('GM-2026-014', 1, 4, '1902', 'Penthouse Suite', 19, '2026-06-01', '2026-06-05', 2, 1500.00, 'USD', 'CONFIRMED', 'Suite upgrade if available, champagne', TRUE),
('GM-2026-015', 1, 1, '1708', 'Executive Suite', 17, '2026-06-02', '2026-06-07', 2, 850.00, 'USD', 'CONFIRMED', 'Anniversary dinner June 3, city view', TRUE),
('GM-2026-016', 1, 5, '0803', 'Superior Double', 8, '2026-06-03', '2026-06-05', 1, 420.00, 'USD', 'CONFIRMED', NULL, FALSE),
('GM-2026-017', 1, 3, '1205', 'Deluxe King', 12, '2026-06-04', '2026-06-07', 1, 550.00, 'USD', 'CONFIRMED', 'Japanese newspaper, green tea, quiet floor', FALSE),
('GM-2026-018', 1, 2, '1008', 'Standard King', 10, '2026-06-05', '2026-06-08', 1, 380.00, 'USD', 'CONFIRMED', 'Gluten-free meals', FALSE),
('GM-2026-019', 1, 4, '1605', 'Deluxe King', 16, '2026-06-06', '2026-06-09', 2, 550.00, 'USD', 'CONFIRMED', 'Roses in room', TRUE),
('GM-2026-020', 1, 1, '1404', 'Deluxe King', 14, '2026-06-07', '2026-06-10', 2, 550.00, 'USD', 'CONFIRMED', 'Vegetarian, extra pillows', TRUE),

-- Week of June 8 - June 14
('GM-2026-021', 1, 5, '0712', 'Standard King', 7, '2026-06-08', '2026-06-10', 2, 380.00, 'USD', 'CONFIRMED', NULL, FALSE),
('GM-2026-022', 1, 3, '1308', 'Deluxe King', 13, '2026-06-09', '2026-06-12', 1, 550.00, 'USD', 'CONFIRMED', 'Green tea, early check-in if possible', FALSE),
('GM-2026-023', 1, 4, '1805', 'Executive Suite', 18, '2026-06-09', '2026-06-14', 2, 850.00, 'USD', 'CONFIRMED', 'Champagne, spa package pre-booked', TRUE),
('GM-2026-024', 1, 2, '1506', 'Superior King', 15, '2026-06-10', '2026-06-13', 1, 480.00, 'USD', 'CONFIRMED', 'Hypoallergenic bedding', FALSE),
('GM-2026-025', 1, 1, '1710', 'Executive Suite', 17, '2026-06-11', '2026-06-15', 2, 850.00, 'USD', 'CONFIRMED', 'City view, vegetarian, extra pillows', TRUE),
('GM-2026-026', 1, 5, '0905', 'Superior Double', 9, '2026-06-12', '2026-06-14', 2, 420.00, 'USD', 'CONFIRMED', 'Late checkout preferred', FALSE),
('GM-2026-027', 1, 3, '1104', 'Deluxe Twin', 11, '2026-06-13', '2026-06-16', 1, 480.00, 'USD', 'CONFIRMED', 'Japanese newspaper', FALSE),
('GM-2026-028', 1, 4, '1902', 'Penthouse Suite', 19, '2026-06-14', '2026-06-18', 2, 1500.00, 'USD', 'CONFIRMED', 'Birthday celebration, champagne, cake', TRUE),

-- Week of June 15 - June 21
('GM-2026-029', 1, 2, '1205', 'Deluxe King', 12, '2026-06-15', '2026-06-19', 1, 550.00, 'USD', 'CONFIRMED', 'Hypoallergenic bedding, quiet floor', FALSE),
('GM-2026-030', 1, 1, '1808', 'Penthouse Suite', 18, '2026-06-16', '2026-06-20', 2, 1500.00, 'USD', 'CONFIRMED', 'Vegetarian, anniversary dinner June 18', TRUE),
('GM-2026-031', 1, 5, '0803', 'Superior Double', 8, '2026-06-17', '2026-06-19', 2, 420.00, 'USD', 'CONFIRMED', NULL, FALSE),
('GM-2026-032', 1, 3, '1402', 'Deluxe King', 14, '2026-06-18', '2026-06-21', 1, 550.00, 'USD', 'CONFIRMED', 'Green tea, Japanese newspaper', FALSE),
('GM-2026-033', 1, 4, '1705', 'Executive Suite', 17, '2026-06-19', '2026-06-23', 2, 850.00, 'USD', 'CONFIRMED', 'Champagne, late checkout', TRUE),
('GM-2026-034', 1, 2, '1008', 'Standard King', 10, '2026-06-20', '2026-06-22', 1, 380.00, 'USD', 'CONFIRMED', 'Gluten-free breakfast', FALSE),
('GM-2026-035', 1, 5, '1104', 'Deluxe Twin', 11, '2026-06-21', '2026-06-24', 2, 480.00, 'USD', 'CONFIRMED', 'Late checkout preferred', FALSE),

-- Week of June 22 - June 28
('GM-2026-036', 1, 1, '1708', 'Executive Suite', 17, '2026-06-22', '2026-06-26', 2, 850.00, 'USD', 'CONFIRMED', 'Extra pillows, city view, vegetarian', TRUE),
('GM-2026-037', 1, 3, '1306', 'Deluxe King', 13, '2026-06-22', '2026-06-25', 1, 550.00, 'USD', 'CONFIRMED', 'Green tea, quiet room', FALSE),
('GM-2026-038', 1, 4, '1801', 'Penthouse Suite', 18, '2026-06-23', '2026-06-27', 2, 1500.00, 'USD', 'CONFIRMED', 'Champagne, roses, spa booking', TRUE),
('GM-2026-039', 1, 2, '1506', 'Superior King', 15, '2026-06-24', '2026-06-27', 1, 480.00, 'USD', 'CONFIRMED', 'Hypoallergenic bedding', FALSE),
('GM-2026-040', 1, 5, '0712', 'Standard King', 7, '2026-06-25', '2026-06-28', 2, 380.00, 'USD', 'CONFIRMED', NULL, FALSE),
('GM-2026-041', 1, 1, '1404', 'Deluxe King', 14, '2026-06-26', '2026-06-29', 2, 550.00, 'USD', 'CONFIRMED', 'Vegetarian meals, extra pillows', TRUE),
('GM-2026-042', 1, 3, '0905', 'Superior Double', 9, '2026-06-27', '2026-06-30', 1, 420.00, 'USD', 'CONFIRMED', 'Green tea', FALSE),
('GM-2026-043', 1, 4, '1605', 'Deluxe King', 16, '2026-06-28', '2026-07-02', 2, 550.00, 'USD', 'CONFIRMED', 'Roses in room, late checkout', TRUE),

-- June 29 - June 30
('GM-2026-044', 1, 2, '1205', 'Deluxe King', 12, '2026-06-29', '2026-07-02', 1, 550.00, 'USD', 'CONFIRMED', 'Hypoallergenic bedding, gluten-free', FALSE),
('GM-2026-045', 1, 5, '0803', 'Superior Double', 8, '2026-06-30', '2026-07-03', 2, 420.00, 'USD', 'CONFIRMED', 'Late checkout preferred', FALSE);
