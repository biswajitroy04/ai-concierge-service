# Implementation Plan: Airport Shuttle Service

## Overview

Implement the Airport Shuttle Service booking system by following the existing layered patterns (Entity → Repository → Service → Controller → AI Tool). Each task builds on the previous ones and is wired together at the end. All code is Java 17, Spring Boot 3.2.5, using Lombok, JPA, Bean Validation, and Springdoc OpenAPI — matching the existing project conventions exactly.

---

## Tasks

- [x] 1. Create the `ShuttleBooking` entity and database migration script
  - Create `src/main/java/com/hotel/concierge/model/ShuttleBooking.java`
    - `@Entity`, `@Table(name = "shuttle_bookings")`, Lombok `@Data @NoArgsConstructor @AllArgsConstructor @Builder`
    - Fields: `id`, `reservation` (`@ManyToOne LAZY` to `Reservation`), `bookingReference` (unique, length 50), `pickupLocation` (length 500), `dropoffLocation` (length 500), `pickupDatetime` (`LocalDateTime`), `passengerCount`, `contactPhone` (length 30), `status` (`@Enumerated(EnumType.STRING)`, default `REQUESTED`), `specialInstructions` (length 1000), `createdAt` (updatable=false), `updatedAt`
    - Inner `enum BookingStatus { REQUESTED, CONFIRMED, IN_PROGRESS, COMPLETED, CANCELLED }`
    - `@PrePersist` and `@PreUpdate` lifecycle callbacks — match `SpaBooking` pattern exactly
    - `@JsonIgnoreProperties` on the `reservation` field — match existing entities
  - Create `src/main/resources/db/shuttle-bookings-migration.sql`
    - `CREATE TABLE IF NOT EXISTS shuttle_bookings` with all columns from the design document
    - Foreign key to `reservations`, unique constraint on `booking_reference`, check constraint `passenger_count >= 1`
    - Indexes on `reservation_id`, `status`, `pickup_datetime`
  - _Requirements: 7.1, 7.2, 7.3, 7.4_

- [x] 2. Create request/response DTOs and exception types
  - Create `src/main/java/com/hotel/concierge/dto/ShuttleBookingRequest.java`
    - Lombok `@Data @NoArgsConstructor @AllArgsConstructor @Builder`
    - Fields with Bean Validation: `pickupLocation` (`@NotBlank`), `dropoffLocation` (`@NotBlank`), `pickupDatetime` (`@NotNull @Future`), `passengerCount` (`@NotNull @Min(1) @Max(10)`), `contactPhone` (`@NotBlank`), `specialInstructions` (optional, no validation annotation)
    - `@Schema` description on every field (Springdoc)
  - Create `src/main/java/com/hotel/concierge/dto/ShuttleBookingResponse.java`
    - Flat DTO: `id`, `bookingReference`, `pickupLocation`, `dropoffLocation`, `pickupDatetime`, `passengerCount`, `contactPhone`, `status` (String), `specialInstructions`, `reservationId`, `createdAt`, `updatedAt`
    - `@Schema` description on every field
    - Lombok `@Data @NoArgsConstructor @AllArgsConstructor @Builder`
  - Create `src/main/java/com/hotel/concierge/exception/ShuttleBookingNotFoundException.java`
    - Extends `RuntimeException`, single-argument constructor with message
  - Create `src/main/java/com/hotel/concierge/exception/ShuttleBookingAccessDeniedException.java`
    - Extends `RuntimeException`, single-argument constructor with message
  - Create `src/main/java/com/hotel/concierge/exception/InvalidStatusTransitionException.java`
    - Extends `RuntimeException`, single-argument constructor with message
  - Add `@ExceptionHandler` entries to `GlobalExceptionHandler` for `ShuttleBookingNotFoundException` (→ 404) and `ShuttleBookingAccessDeniedException` (→ 403)
  - _Requirements: 2.3, 3.2, 4.2, 4.4, 8.1, 8.4, 9.2_

- [x] 3. Create `ShuttleBookingRepository`
  - Create `src/main/java/com/hotel/concierge/repository/ShuttleBookingRepository.java`
    - `@Repository` interface extending `JpaRepository<ShuttleBooking, Long>`
    - `List<ShuttleBooking> findByReservationIdOrderByPickupDatetimeAsc(Long reservationId)`
    - `long countByReservationId(Long reservationId)` — used by service for reference number generation
    - `Optional<ShuttleBooking> findByBookingReference(String bookingReference)` — used by cancel-by-reference tool
    - `@Query` method `findByHotelWithFilters(@Param("hotelId") Long, @Param("status") BookingStatus, @Param("pickupDate") LocalDate)` — see design document for JPQL
  - _Requirements: 2.1, 2.2, 2.4, 6.1, 6.2, 6.3_

- [x] 4. Implement `ShuttleBookingService`
  - Create `src/main/java/com/hotel/concierge/service/ShuttleBookingService.java`
    - `@Service @RequiredArgsConstructor @Transactional`
    - Inject `ShuttleBookingRepository`, `ReservationRepository`
  - `createBooking(Long reservationId, ShuttleBookingRequest request)`:
    - Load `Reservation` via `ReservationRepository` or throw `RuntimeException("Reservation not found")`
    - Validate `pickupDatetime` is after `LocalDateTime.now()` — throw `IllegalArgumentException` with descriptive message if not (Bean Validation's `@Future` covers this at the controller layer; service layer adds defence-in-depth)
    - Validate passenger count 1–10 (defence-in-depth)
    - Generate `bookingReference`: `String.format("SHU-%d-%03d", reservationId, countByReservationId + 1)`
    - Build and save `ShuttleBooking` with `status = REQUESTED`
    - Return saved entity
  - `getBookingById(Long bookingId)`:
    - Fetch by ID, throw `ShuttleBookingNotFoundException` if absent
  - `getBookingsByReservation(Long reservationId)`:
    - Return `findByReservationIdOrderByPickupDatetimeAsc`
  - `getBookingsByHotel(Long hotelId, BookingStatus status, LocalDate pickupDate)`:
    - Delegate to `findByHotelWithFilters`
  - `updateStatus(Long bookingId, ShuttleBooking.BookingStatus newStatus)`:
    - Fetch booking, throw `ShuttleBookingNotFoundException` if absent
    - Enforce allowed transitions using a `Set` or `switch` — throw `InvalidStatusTransitionException` for forbidden transitions (e.g. COMPLETED → CANCELLED, IN_PROGRESS → CANCELLED)
    - Save and return updated entity
  - `cancelBooking(Long bookingId, Long reservationId)`:
    - Fetch booking, throw `ShuttleBookingNotFoundException` if absent
    - Check `booking.getReservation().getId().equals(reservationId)` — throw `ShuttleBookingAccessDeniedException` if mismatch
    - Check status is `REQUESTED` or `CONFIRMED` — throw `InvalidStatusTransitionException` if not
    - Set status to `CANCELLED`, save, return
  - _Requirements: 1.1, 1.2, 1.4, 1.5, 2.1, 2.4, 3.1, 3.2, 4.1, 4.2, 4.4, 6.1_

- [x]* 4.1 Write property tests for `ShuttleBookingService`
  - Add jqwik dependency to `pom.xml` (`net.jqwik:jqwik:1.8.4`, test scope)
  - Create `ShuttleBookingServicePropertyTest.java` using `@ExtendWith(MockitoExtension.class)` + jqwik `@Property`
  - **Property 1: Valid booking creation stores REQUESTED status** — generate arbitrary `pickupLocation`, `dropoffLocation`, future `pickupDatetime`, `passengerCount` in [1,10], non-blank `contactPhone`; verify returned status = REQUESTED and all fields match
  - **Property 2: Booking reference format and uniqueness** — for N in [2,10] bookings on same reservation, verify all refs match `SHU-\\d+-\\d+` and all are distinct (stub `countByReservationId` to increment)
  - **Property 4: Past pickup datetime always rejected** — generate arbitrary past datetimes, verify service throws
  - **Property 5: Special instructions round-trip** — generate arbitrary strings, verify retrieved booking has identical `specialInstructions`
  - **Property 9: Valid status transitions succeed** — apply REQUESTED→CONFIRMED→IN_PROGRESS→COMPLETED in sequence, verify each stored status
  - **Property 10: Invalid state transitions are rejected** — for status in {COMPLETED, IN_PROGRESS} attempt CANCELLED transition, verify `InvalidStatusTransitionException`
  - **Property 11: Eligible cancellations transition to CANCELLED** — for status in {REQUESTED, CONFIRMED} and matching reservationId, verify cancel sets CANCELLED
  - **Property 12: Ownership mismatch produces access denied** — create booking for reservation A, call cancel with reservation B ID, verify `ShuttleBookingAccessDeniedException`
  - **Property 16: Timestamps set on create and updated on state change** — verify `createdAt` non-null after create, `updatedAt` >= `createdAt`; after update, new `updatedAt` >= previous `updatedAt`
  - _Requirements: 1.1, 1.2, 1.5, 1.6, 3.1, 3.2, 4.1, 4.2, 4.4, 7.4_

- [x] 5. Implement `ShuttleBookingController`
  - Create `src/main/java/com/hotel/concierge/controller/ShuttleBookingController.java`
    - Annotations: `@RestController @RequiredArgsConstructor @Transactional(readOnly = true) @Tag(name = "Airport Shuttle", description = "Airport shuttle transportation booking endpoints")`
    - Inject `ShuttleBookingService`
    - `POST /shuttle-bookings` (`@Transactional`)
      - `@Valid @RequestBody ShuttleBookingRequest` → call `createBooking` → map to `ShuttleBookingResponse` → `ResponseEntity.status(201).body(...)`
      - `@Operation(summary = "Create a new airport shuttle booking")`
    - `GET /shuttle-bookings/{id}`
      - call `getBookingById` → map → `ResponseEntity.ok(...)`
      - `@Operation(summary = "Get a shuttle booking by ID")`
    - `GET /shuttle-bookings/reservation/{reservationId}`
      - call `getBookingsByReservation` → map list → `ResponseEntity.ok(...)`
      - `@Operation(summary = "List all shuttle bookings for a reservation")`
    - `DELETE /shuttle-bookings/{id}` (`@Transactional`)
      - Extract `reservationId` from `@RequestParam` → call `cancelBooking` → map → `ResponseEntity.ok(...)`
      - `@Operation(summary = "Cancel a shuttle booking")`
    - `GET /admin/shuttle-bookings/hotel/{hotelId}`
      - `@RequestParam` optional `status` and `pickupDate` → call `getBookingsByHotel` → map list → `ResponseEntity.ok(...)`
      - `@Operation(summary = "Get all shuttle bookings for a hotel (admin)")`
    - `PUT /admin/shuttle-bookings/{id}/status` (`@Transactional`)
      - `@RequestParam String status` → parse enum (catch `IllegalArgumentException` → throw descriptive `RuntimeException` for 400) → call `updateStatus` → map → `ResponseEntity.ok(...)`
      - `@Operation(summary = "Update shuttle booking status (admin)")`
    - Create `ShuttleBookingMapper` static helper (or inner class) with `toResponse(ShuttleBooking)` mapping all fields
  - _Requirements: 1.3, 2.1, 2.2, 2.3, 2.4, 3.3, 3.4, 4.3, 6.4, 8.1, 9.1, 9.3_

- [x]* 5.1 Write property tests for `ShuttleBookingController`
  - Create `ShuttleBookingControllerPropertyTest.java` using `@WebMvcTest(ShuttleBookingController.class) @AutoConfigureMockMvc(addFilters = false)` + jqwik `@Property`
  - **Property 3: HTTP 201 returned for any valid booking request** — generate random valid request bodies, POST, assert 201 and body contains `bookingReference` and `status = REQUESTED`
  - **Property 6: Booking list is sorted ascending** — mock service to return shuffled list, GET by reservationId, verify response order matches ascending `pickupDatetime`
  - **Property 15: Missing required fields always produce HTTP 400 with field map** — generate requests with each required field nulled out, POST, assert 400 and `errors` map contains the field name
  - _Requirements: 1.3, 2.1, 8.1_

- [x]* 5.2 Write unit tests for `ShuttleBookingController`
  - Create `ShuttleBookingControllerTest.java` using `@WebMvcTest` + Mockito
  - Test: non-existent booking ID → 404
  - Test: `status = "BLAH"` on PUT → 400
  - Test: cross-reservation cancel (mock service throws `ShuttleBookingAccessDeniedException`) → 403
  - Test: GET `/admin/shuttle-bookings/hotel/1` returns 200 with list
  - _Requirements: 2.3, 3.4, 4.4_

- [x] 6. Add shuttle booking tools to `ConciergeTools` and update the factory
  - Edit `ConciergeTools.java`:
    - Add `ShuttleBookingRepository shuttleBookingRepo` field
    - Update constructor to accept and assign `shuttleBookingRepo`
    - Add `bookAirportShuttle` `@Tool` method:
      - Parse `pickupDate + "T" + pickupTime` to `LocalDateTime`; if past, return error string (no DB write)
      - Validate `passengerCount` in [1,10]; if not, return error string (no DB write)
      - Load `Reservation`, generate `bookingReference`, build and save `ShuttleBooking`, return confirmation string containing `bookingReference`
    - Add `cancelAirportShuttle` `@Tool` method:
      - Find booking by `bookingReference` via `findByBookingReference`; if absent, return "Booking not found" string
      - Check status is REQUESTED or CONFIRMED; if not, return "Cannot cancel" string
      - Set status CANCELLED, save, return confirmation string
  - Edit `ConciergeToolsFactory.java`:
    - Inject `ShuttleBookingRepository` and pass to `ConciergeTools` constructor
  - Edit `ConciergeAiService.buildSystemPrompt` method — add shuttle tool to AVAILABLE TOOLS list and to the guest-facing welcome message quick actions
  - _Requirements: 5.1, 5.2, 5.3, 5.4_

- [x]* 6.1 Write property and unit tests for ConciergeTools shuttle methods
  - Create `ConciergeToolsShuttleTest.java` using `@ExtendWith(MockitoExtension.class)` + jqwik `@Property`
  - **Property 13: Tool confirmation string contains booking reference** — generate valid inputs, call `bookAirportShuttle`, verify returned string matches `.*SHU-\d+-\d+.*`
  - **Property 14: Tool error path produces no booking and returns error message** — generate past dates or out-of-range passenger counts, call tool, verify no `save` called on mock repo and returned string is non-blank
  - Unit test: `cancelAirportShuttle` for non-existent reference → returns not-found string
  - Unit test: `cancelAirportShuttle` for COMPLETED booking → returns cannot-cancel string
  - _Requirements: 5.2, 5.4_

- [x] 7. Checkpoint — verify all components compile and tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 8. Write integration tests with Testcontainers
  - Create `ShuttleBookingIntegrationTest.java` in `src/test` with `@SpringBootTest @AutoConfigureMockMvc @Testcontainers`
  - Use existing MySQL Testcontainers pattern from `pom.xml` (`testcontainers:mysql:1.19.7`)
  - Test cases:
    - **Property 7: Create-then-retrieve round trip** — POST booking, GET by returned ID, verify all fields match
    - **Property 8: Admin hotel filter** — create bookings for two hotel IDs, query each hotel, verify no cross-hotel results
    - Full lifecycle: create REQUESTED → confirm → IN_PROGRESS → COMPLETE — verify DB at each step
    - Cancel REQUESTED booking — verify DB status = CANCELLED
    - Cancel COMPLETED booking — verify 400 response
  - _Requirements: 1.1, 2.2, 2.4, 3.1, 4.1, 4.2, 6.1_

- [x] 9. Final checkpoint — all tests pass and documentation is reachable
  - Run `mvn test` to confirm all unit, property, and integration tests pass.
  - Verify `/swagger-ui.html` shows the "Airport Shuttle" tag with all six endpoints documented.
  - Ensure all tests pass, ask the user if questions arise.

---

## Task Dependency Graph

```json
{
  "waves": [
    { "wave": 1, "tasks": ["1"] },
    { "wave": 2, "tasks": ["2"] },
    { "wave": 3, "tasks": ["3"] },
    { "wave": 4, "tasks": ["4"] },
    { "wave": 5, "tasks": ["4.1", "5"] },
    { "wave": 6, "tasks": ["5.1", "5.2", "6"] },
    { "wave": 7, "tasks": ["6.1", "7"] },
    { "wave": 8, "tasks": ["8"] },
    { "wave": 9, "tasks": ["9"] }
  ]
}
```

---

## Notes

- Tasks marked `*` are optional and can be skipped for a faster MVP, but they cover the correctness properties from the design document.
- The jqwik dependency added in task 4.1 is required for all `*` property test tasks.
- `shuttle-bookings-migration.sql` should be run manually against the target database (the project uses `ddl-auto: update`, so JPA will also create the table automatically on first startup — the SQL file is for documentation and explicit migration use).
- No existing files are deleted or structurally refactored; only additive changes are made to `ConciergeTools.java`, `ConciergeToolsFactory.java`, `ConciergeAiService.java`, and `GlobalExceptionHandler.java`.
- Each task references specific requirements for traceability back to `requirements.md`.
- Property tests validate universal correctness invariants; unit tests cover specific examples and edge cases; integration tests verify end-to-end behaviour including the database.
