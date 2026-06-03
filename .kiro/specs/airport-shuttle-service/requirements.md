# Requirements Document

## Introduction

The Airport Shuttle Service feature adds a complete transportation booking system to the AI Concierge Platform. Hotel guests can request shuttle pickups to or from the airport via the AI chat interface or directly through a dedicated REST API. Hotel staff can manage bookings through the existing Admin API. The feature follows the same architectural and coding conventions already established for Spa Bookings and Restaurant Bookings in the platform.

## Glossary

- **ShuttleBooking**: A single airport shuttle transportation request linked to a hotel Reservation.
- **Booking_Service**: The `ShuttleBookingService` Spring service component responsible for shuttle booking business logic.
- **Booking_Controller**: The `ShuttleBookingController` REST controller that exposes the public and admin shuttle endpoints.
- **Booking_Repository**: The `ShuttleBookingRepository` Spring Data JPA repository for persisting shuttle bookings.
- **AI_Concierge**: The existing `ConciergeTools` + `ConciergeAiService` infrastructure that exposes hotel services to guests via natural-language chat.
- **Admin**: A hotel staff member with the ADMIN, MANAGER, or FRONT_DESK role in the existing `AppUser.UserRole` enum.
- **Guest**: A hotel guest identified by a Reservation obtained via JWT/QR-code authentication.
- **Reservation**: An existing hotel stay record in the `reservations` table, the parent entity for all guest-level bookings.
- **Pickup_Location**: A free-text field describing the origin address or hotel property name for the shuttle pickup.
- **Dropoff_Location**: A free-text field describing the destination address, typically an airport name or terminal.
- **Booking_Status**: The lifecycle state of a shuttle booking: `REQUESTED`, `CONFIRMED`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`.
- **Passenger_Count**: The number of passengers travelling on the shuttle, minimum 1, maximum 10.
- **Contact_Phone**: The guest-provided phone number for driver contact on pickup day.

---

## Requirements

### Requirement 1: Create a Shuttle Booking

**User Story:** As a hotel guest, I want to book an airport shuttle, so that I can arrange guaranteed transportation between the hotel and the airport.

#### Acceptance Criteria

1. WHEN a guest submits a shuttle booking request with a valid reservation ID, pickup location, dropoff location, pickup date, pickup time, passenger count, and contact phone, THE Booking_Service SHALL persist a new ShuttleBooking record with status `REQUESTED`.
2. WHEN a guest submits a shuttle booking request, THE Booking_Service SHALL assign a unique booking reference number in the format `SHU-<reservationId>-<sequentialNumber>` (e.g. `SHU-42-001`).
3. WHEN a shuttle booking is successfully created and the full booking data is available, THE Booking_Controller SHALL return HTTP 201 with the full ShuttleBooking representation including the assigned booking reference and status `REQUESTED`.
4. WHEN a guest provides a passenger count less than 1 or greater than 10, THE Booking_Controller SHALL return HTTP 400 with a descriptive validation error message.
5. WHEN a guest provides a pickup date-time that is in the past relative to the current server time, THE Booking_Service SHALL reject the request and return a descriptive error.
6. WHERE special instructions are provided by the guest, THE Booking_Service SHALL persist them alongside the booking without modification.

---

### Requirement 2: Retrieve Shuttle Bookings

**User Story:** As a hotel guest or staff member, I want to view shuttle bookings, so that I can confirm or review transportation arrangements.

#### Acceptance Criteria

1. WHEN a request is made with a valid reservation ID, THE Booking_Controller SHALL return HTTP 200 with the list of all ShuttleBooking records linked to that reservation, ordered by pickup date-time ascending.
2. WHEN a request is made with a valid booking ID, THE Booking_Controller SHALL return HTTP 200 with the single ShuttleBooking record matching that ID.
3. WHEN a booking ID does not exist in the system, THE Booking_Controller SHALL return HTTP 404 with a descriptive error message.
4. WHEN an Admin requests all shuttle bookings for a hotel, THE Booking_Controller SHALL return HTTP 200 with all ShuttleBooking records for reservations belonging to that hotel.

---

### Requirement 3: Update Booking Status (Admin)

**User Story:** As hotel staff, I want to confirm, start, and complete shuttle bookings, so that I can keep guests informed of their transportation status.

#### Acceptance Criteria

1. WHEN an Admin provides a valid booking ID and a target status of `CONFIRMED`, `IN_PROGRESS`, or `COMPLETED`, THE Booking_Service SHALL update the booking status to the requested value.
2. WHEN an Admin attempts to transition a booking to `CANCELLED` after it is already `COMPLETED` or `IN_PROGRESS`, THE Booking_Service SHALL reject the transition and return a descriptive error.
3. WHEN a status update succeeds, THE Booking_Controller SHALL return HTTP 200 with the updated ShuttleBooking representation.
4. WHEN an Admin provides a status value that is not a member of the `BookingStatus` enum, THE Booking_Controller SHALL return HTTP 400 with a validation error.

---

### Requirement 4: Cancel a Shuttle Booking

**User Story:** As a hotel guest, I want to cancel my shuttle booking before the pickup time, so that I can change my travel plans without penalty.

#### Acceptance Criteria

1. WHEN a guest requests cancellation of a shuttle booking with status `REQUESTED` or `CONFIRMED`, THE Booking_Service SHALL first verify the booking belongs to the guest's reservation and then set the booking status to `CANCELLED`.
2. WHEN a guest requests cancellation of a booking with status `IN_PROGRESS` or `COMPLETED`, THE Booking_Service SHALL reject the cancellation and return a descriptive error.
3. WHEN a cancellation is successful, THE Booking_Controller SHALL return HTTP 200 with the updated ShuttleBooking representation showing status `CANCELLED`.
4. WHEN a guest provides a booking ID that does not belong to their reservation, THE Booking_Service SHALL reject the cancellation request and return HTTP 403 before checking any other conditions.

---

### Requirement 5: AI Concierge Integration

**User Story:** As a hotel guest using the AI chat, I want to book and cancel airport shuttles through natural-language conversation, so that I can arrange transportation without leaving the chat interface.

#### Acceptance Criteria

1. WHEN the AI_Concierge receives a guest request to book an airport shuttle, THE AI_Concierge SHALL invoke the `bookAirportShuttle` tool with the extracted pickup location, dropoff location, pickup date, pickup time, passenger count, and optional special instructions.
2. WHEN the `bookAirportShuttle` tool executes successfully, THE AI_Concierge SHALL return a confirmation message including the booking reference number, pickup date-time, passenger count, and a note that the booking is pending confirmation by hotel staff.
3. WHEN the AI_Concierge receives a request to cancel an existing shuttle booking, THE AI_Concierge SHALL invoke the `cancelAirportShuttle` tool with the booking reference.
4. IF the `bookAirportShuttle` tool encounters an error (invalid date, capacity exceeded, etc.), THEN THE AI_Concierge SHALL return a descriptive error message to the guest without creating a booking.

---

### Requirement 6: Booking History and Admin Management

**User Story:** As hotel staff, I want a consolidated view of all shuttle bookings, so that I can plan driver assignments and vehicle availability.

#### Acceptance Criteria

1. THE Booking_Controller SHALL expose an admin endpoint that returns all shuttle bookings for a given hotel, optionally filterable by status and pickup date.
2. WHEN filtering by status, THE Booking_Repository SHALL return only records whose `status` column matches the provided `BookingStatus` value.
3. WHEN filtering by pickup date, THE Booking_Repository SHALL return only records whose `pickup_datetime` date portion matches the provided date.
4. THE Booking_Controller SHALL expose all admin shuttle endpoints under the `/admin/shuttle-bookings` path prefix, consistent with the existing `/admin/**` security permit-all configuration.

---

### Requirement 7: Data Persistence and Schema

**User Story:** As a system architect, I want a well-structured database schema for shuttle bookings, so that the data is consistent with the rest of the platform and can be queried efficiently.

#### Acceptance Criteria

1. THE Booking_Service SHALL persist shuttle bookings in a dedicated `shuttle_bookings` MySQL table with columns: `id`, `reservation_id`, `booking_reference`, `pickup_location`, `dropoff_location`, `pickup_datetime`, `passenger_count`, `contact_phone`, `status`, `special_instructions`, `created_at`, `updated_at`.
2. THE `shuttle_bookings` table SHALL declare a foreign key constraint on `reservation_id` referencing the `reservations` table.
3. THE `shuttle_bookings` table SHALL declare a unique constraint on `booking_reference` and a check constraint requiring `passenger_count >= 1`.
4. THE Booking_Service SHALL set `created_at` on record creation and `updated_at` on every status update via JPA lifecycle callbacks, consistent with existing entities.

---

### Requirement 8: Input Validation and Exception Handling

**User Story:** As a developer, I want all shuttle booking inputs to be validated and errors to be handled consistently, so that the API is robust and returns predictable error responses.

#### Acceptance Criteria

1. WHEN a request body is missing a required field (pickup location, dropoff location, pickup datetime, passenger count, or contact phone), THE Booking_Controller SHALL return HTTP 400 with a field-level validation error map consistent with the existing `GlobalExceptionHandler`.
2. WHEN a passenger count is provided outside the range 1–10, THE Booking_Service SHALL reject the request with HTTP 400. The response SHOULD include a message stating the allowed range, but HTTP 400 SHALL be returned regardless of whether the range message is included.
3. WHEN an internal error occurs during booking creation or retrieval, THE Booking_Service SHALL log the error and THE Booking_Controller SHALL return HTTP 500 via the existing `GlobalExceptionHandler`.
4. THE Booking_Controller SHALL use Bean Validation annotations (`@Valid`, `@NotBlank`, `@Min`, `@Max`, `@NotNull`, `@Future`) on the request DTO consistent with the `spring-boot-starter-validation` dependency already present in the project.

---

### Requirement 9: API Documentation

**User Story:** As an API consumer, I want complete OpenAPI/Swagger documentation for the shuttle booking endpoints, so that I can integrate with or test the API without reading source code.

#### Acceptance Criteria

1. THE Booking_Controller SHALL annotate every endpoint with `@Operation(summary = ...)` and `@Tag(name = "Airport Shuttle", description = "...")` using the `springdoc-openapi-starter-webmvc-ui` library already present in the project.
2. THE ShuttleBooking request and response DTOs SHALL annotate all fields with `@Schema` descriptions so that Swagger UI renders field-level documentation.
3. THE Booking_Controller SHALL be accessible via the existing Swagger UI at `/swagger-ui.html` without requiring additional configuration.
