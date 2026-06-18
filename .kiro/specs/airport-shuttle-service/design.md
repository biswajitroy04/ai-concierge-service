# Design Document: Airport Shuttle Service

## Overview

The Airport Shuttle Service adds a guest-facing transportation booking capability to the existing AI Concierge Platform. It follows the same layered architecture (Controller → Service → Repository → Entity) already established for `SpaBooking` and `RestaurantBooking`. Guests can book shuttles through the AI chat (via a new LangChain4J tool) or directly via REST. Hotel staff manage bookings through new admin endpoints that follow the `/admin/**` pattern.

The feature introduces one new entity (`ShuttleBooking`), one new table (`shuttle_bookings`), four new classes in the booking lifecycle layer, and two new tools in `ConciergeTools`. No existing entities or tables are altered, ensuring full backward compatibility.

---

## Architecture

### Integration into the Existing System

```
┌──────────────────────────────────────────────────────────────────┐
│                      CLIENT LAYER                                  │
├──────────────────────┬───────────────────────────────────────────┤
│  Guest Chat (QR JWT) │  Admin Panel / REST Client                │
└──────────┬───────────┴──────────────────┬────────────────────────┘
           │                              │
           ▼                              ▼
┌──────────────────────┐     ┌────────────────────────────────┐
│  ConciergeTools       │     │  ShuttleBookingController       │
│  (AI Tool Layer)      │     │  POST /shuttle-bookings         │
│  bookAirportShuttle() │     │  GET  /shuttle-bookings/{id}    │
│  cancelAirportShuttle │     │  GET  /shuttle-bookings/res/{id}│
│  (new tools, same     │     │  DELETE /shuttle-bookings/{id}  │
│   per-session pattern)│     │  GET  /admin/shuttle-bookings   │
└──────────┬───────────┘     │  PUT  /admin/shuttle-bookings/  │
           │                 │        {id}/status               │
           └────────┬────────┘
                    │
           ┌────────▼────────┐
           │ ShuttleBooking   │
           │ Service          │
           │ (business logic) │
           └────────┬────────┘
                    │
           ┌────────▼────────┐
           │ ShuttleBooking   │
           │ Repository       │
           │ (JPA)            │
           └────────┬────────┘
                    │
           ┌────────▼────────┐
           │  MySQL           │
           │  shuttle_bookings│
           └─────────────────┘
```

### Component Placement

| New Component | Package | Analogous Existing Component |
|---|---|---|
| `ShuttleBooking` | `model` | `SpaBooking` |
| `ShuttleBookingRepository` | `repository` | `SpaBookingRepository` |
| `ShuttleBookingService` | `service` | `DashboardService` |
| `ShuttleBookingController` | `controller` | `AdminController` |
| `ShuttleBookingRequest` (DTO) | `dto` | — |
| `ShuttleBookingResponse` (DTO) | `dto` | — |
| `ShuttleBookingMapper` | `service` | — |
| `bookAirportShuttle` tool | `service/ai/tools/ConciergeTools` | `bookSpaAppointment` |
| `cancelAirportShuttle` tool | `service/ai/tools/ConciergeTools` | — |

---

## Components and Interfaces

### ShuttleBookingController

**Path prefix:** `/shuttle-bookings` (guest-facing), `/admin/shuttle-bookings` (admin)

**Security:** Guest endpoints require a valid JWT (either Bearer or QR token). Admin endpoints are under `/admin/**`, which the existing `SecurityConfig` already allows without additional configuration.

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/shuttle-bookings` | JWT (guest or admin) | Create a new shuttle booking |
| `GET` | `/shuttle-bookings/{id}` | JWT | Get a booking by ID |
| `GET` | `/shuttle-bookings/reservation/{reservationId}` | JWT | List all bookings for a reservation |
| `DELETE` | `/shuttle-bookings/{id}` | JWT | Cancel a booking (ownership enforced) |
| `GET` | `/admin/shuttle-bookings/hotel/{hotelId}` | None (permit-all) | List bookings for a hotel |
| `PUT` | `/admin/shuttle-bookings/{id}/status` | None (permit-all) | Update booking status |

**Annotations:**
```java
@RestController
@RequiredArgsConstructor
@Tag(name = "Airport Shuttle", description = "Airport shuttle transportation booking endpoints")
```

### ShuttleBookingService

Responsibilities:
- Validate business rules (future date, passenger range, ownership).
- Generate `bookingReference` using format `SHU-%d-%03d` (reservationId, count + 1).
- Delegate persistence to `ShuttleBookingRepository`.
- Enforce state-machine transitions.

```java
@Service
@RequiredArgsConstructor
@Transactional
public class ShuttleBookingService {
    ShuttleBooking createBooking(Long reservationId, ShuttleBookingRequest request);
    ShuttleBooking getBookingById(Long bookingId);
    List<ShuttleBooking> getBookingsByReservation(Long reservationId);
    List<ShuttleBooking> getBookingsByHotel(Long hotelId, BookingStatus status, LocalDate pickupDate);
    ShuttleBooking updateStatus(Long bookingId, ShuttleBooking.BookingStatus newStatus);
    ShuttleBooking cancelBooking(Long bookingId, Long reservationId);
}
```

### ShuttleBookingRepository

```java
@Repository
public interface ShuttleBookingRepository extends JpaRepository<ShuttleBooking, Long> {

    List<ShuttleBooking> findByReservationIdOrderByPickupDatetimeAsc(Long reservationId);

    long countByReservationId(Long reservationId);

    @Query("SELECT s FROM ShuttleBooking s " +
           "WHERE s.reservation.hotel.id = :hotelId " +
           "AND (:status IS NULL OR s.status = :status) " +
           "AND (:pickupDate IS NULL OR FUNCTION('DATE', s.pickupDatetime) = :pickupDate)")
    List<ShuttleBooking> findByHotelWithFilters(
        @Param("hotelId") Long hotelId,
        @Param("status") ShuttleBooking.BookingStatus status,
        @Param("pickupDate") LocalDate pickupDate);
}
```

### ConciergeTools Extension

Two new `@Tool` methods are added to the existing `ConciergeTools` class. The `ShuttleBookingRepository` is injected via the updated `ConciergeToolsFactory`.

```java
@Tool("Book an airport shuttle for the guest. Parameters: pickupLocation (text, e.g. 'Hotel Main Entrance'), " +
      "dropoffLocation (text, e.g. 'JFK Terminal 4'), pickupDate (YYYY-MM-DD), pickupTime (HH:MM), " +
      "passengerCount (1-10), specialInstructions (optional)")
public String bookAirportShuttle(String pickupLocation, String dropoffLocation,
                                  String pickupDate, String pickupTime,
                                  int passengerCount, String specialInstructions);

@Tool("Cancel an existing airport shuttle booking. Parameters: bookingReference (e.g. SHU-42-001)")
public String cancelAirportShuttle(String bookingReference);
```

---

## Data Models

### ShuttleBooking Entity

```java
@Entity
@Table(name = "shuttle_bookings")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ShuttleBooking {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
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
```

### ShuttleBookingRequest DTO

```java
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ShuttleBookingRequest {
    @NotBlank @Schema(description = "Origin address or hotel area", example = "Hotel Main Entrance")
    private String pickupLocation;

    @NotBlank @Schema(description = "Destination, typically airport and terminal", example = "JFK Terminal 4")
    private String dropoffLocation;

    @NotNull @Future @Schema(description = "Pickup date and time (future)", example = "2026-06-15T09:30:00")
    private LocalDateTime pickupDatetime;

    @NotNull @Min(1) @Max(10) @Schema(description = "Number of passengers (1-10)", example = "2")
    private Integer passengerCount;

    @NotBlank @Schema(description = "Guest contact phone for driver", example = "+1-555-0100")
    private String contactPhone;

    @Schema(description = "Optional special instructions", example = "Large luggage, need help loading")
    private String specialInstructions;
}
```

### ShuttleBookingResponse DTO

```java
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ShuttleBookingResponse {
    @Schema(description = "Booking database ID")
    private Long id;
    @Schema(description = "Unique booking reference", example = "SHU-42-001")
    private String bookingReference;
    private String pickupLocation;
    private String dropoffLocation;
    private LocalDateTime pickupDatetime;
    private Integer passengerCount;
    private String contactPhone;
    @Schema(description = "Booking lifecycle status")
    private String status;
    private String specialInstructions;
    private Long reservationId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

### Database Migration

New file: `src/main/resources/db/shuttle-bookings-migration.sql`

```sql
-- Airport Shuttle Service: Add shuttle_bookings table
-- Run after schema.sql (or add at end of schema.sql for fresh installs)

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
```

---

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system — essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Valid booking creation stores REQUESTED status

*For any* valid `ShuttleBookingRequest` (future datetime, passenger count 1–10, non-blank required fields), calling `ShuttleBookingService.createBooking` SHALL return a `ShuttleBooking` whose `status` is `REQUESTED` and whose field values exactly match the input.

**Validates: Requirements 1.1**

---

### Property 2: Booking reference format and uniqueness

*For any* sequence of N valid bookings created for the same reservation, all returned `bookingReference` values SHALL match the pattern `SHU-\d+-\d+` and SHALL all be distinct from each other.

**Validates: Requirements 1.2**

---

### Property 3: Invalid passenger count always produces HTTP 400

*For any* integer outside the range [1, 10] (including zero, negatives, and values > 10), submitting it as `passengerCount` in a booking request SHALL result in an HTTP 400 response.

**Validates: Requirements 1.4, 8.2**

---

### Property 4: Past pickup datetime is always rejected

*For any* `LocalDateTime` that is strictly before the current server time, submitting it as `pickupDatetime` SHALL result in a rejection (service throws or returns an error; no booking is persisted).

**Validates: Requirements 1.5**

---

### Property 5: Special instructions round-trip

*For any* non-null string `s` provided as `specialInstructions` in a booking request, the persisted `ShuttleBooking` retrieved by ID SHALL have `specialInstructions` equal to `s` without modification.

**Validates: Requirements 1.6**

---

### Property 6: Booking list is sorted ascending by pickup datetime

*For any* set of N shuttle bookings with distinct `pickupDatetime` values created in arbitrary order for the same reservation, calling `getBookingsByReservation` SHALL return them in ascending `pickupDatetime` order.

**Validates: Requirements 2.1**

---

### Property 7: Create-then-retrieve round trip

*For any* valid booking request, the `ShuttleBooking` returned by `createBooking` SHALL equal the `ShuttleBooking` returned by `getBookingById` using the created booking's ID.

**Validates: Requirements 2.2**

---

### Property 8: Admin hotel filter returns only that hotel's bookings

*For any* two distinct hotels H1 and H2 each having reservations with shuttle bookings, querying by H1's hotel ID SHALL return only bookings whose reservation belongs to H1, with zero bookings from H2's reservations.

**Validates: Requirements 2.4, 6.1, 6.2, 6.3**

---

### Property 9: Valid status transitions succeed

*For any* shuttle booking in status `REQUESTED`, transitioning to `CONFIRMED`, then to `IN_PROGRESS`, then to `COMPLETED` SHALL succeed at each step, and the stored status SHALL equal the requested target status after each transition.

**Validates: Requirements 3.1, 3.3**

---

### Property 10: Invalid state transitions are rejected

*For any* shuttle booking in status `COMPLETED` or `IN_PROGRESS`, attempting to transition to `CANCELLED` SHALL be rejected with an exception, and the booking status SHALL remain unchanged.

**Validates: Requirements 3.2, 4.2**

---

### Property 11: Eligible cancellations transition to CANCELLED

*For any* shuttle booking in status `REQUESTED` or `CONFIRMED` owned by a given reservation, calling `cancelBooking` with the owning reservation ID SHALL set the booking status to `CANCELLED`.

**Validates: Requirements 4.1, 4.3**

---

### Property 12: Ownership mismatch produces HTTP 403

*For any* shuttle booking owned by reservation A, calling `cancelBooking` with reservation B's ID (B ≠ A) SHALL result in an `AccessDeniedException` or HTTP 403 response, with no change to the booking status.

**Validates: Requirements 4.4**

---

### Property 13: Tool confirmation string contains booking reference

*For any* valid combination of pickup/dropoff locations, future date, and passenger count 1–10, calling the `bookAirportShuttle` tool method SHALL return a non-empty string containing a booking reference matching the pattern `SHU-\d+-\d+`.

**Validates: Requirements 5.2**

---

### Property 14: Tool error path produces no booking and returns error message

*For any* invalid input (past date or passenger count outside [1,10]) passed to the `bookAirportShuttle` tool, the tool SHALL return an error string AND no `ShuttleBooking` record SHALL be created in the database.

**Validates: Requirements 5.4**

---

### Property 15: Missing required fields always produce HTTP 400 with field map

*For any* request body where at least one required field (`pickupLocation`, `dropoffLocation`, `pickupDatetime`, `passengerCount`, `contactPhone`) is null or blank, the controller SHALL return HTTP 400 with a response body containing an `errors` map that includes the missing field name(s).

**Validates: Requirements 8.1**

---

### Property 16: Timestamps set on create and updated on state change

*For any* shuttle booking, `createdAt` SHALL be non-null immediately after creation, and `updatedAt` SHALL be greater than or equal to `createdAt`. After any status update, the new `updatedAt` SHALL be greater than or equal to the previous `updatedAt`.

**Validates: Requirements 7.4**

---

## Error Handling

### Exception Types and HTTP Mappings

All exceptions flow through the existing `GlobalExceptionHandler` (`@RestControllerAdvice`). The new feature introduces two new exception types placed in the `exception` package:

| Exception | HTTP Status | When thrown |
|---|---|---|
| `ShuttleBookingNotFoundException` extends `RuntimeException` | 404 | Booking ID not found |
| `ShuttleBookingAccessDeniedException` extends `RuntimeException` | 403 | Booking does not belong to requesting reservation |
| `InvalidStatusTransitionException` extends `RuntimeException` | 400 | Illegal state machine transition |
| `MethodArgumentNotValidException` (existing) | 400 | Bean Validation failure on request DTO |

`GlobalExceptionHandler` already handles `RuntimeException` (→ 400) and generic `Exception` (→ 500). New exception types extend `RuntimeException` and are handled by the existing catch-all. Dedicated `@ExceptionHandler` entries can be added to `GlobalExceptionHandler` to map `ShuttleBookingNotFoundException` to 404 and `ShuttleBookingAccessDeniedException` to 403.

### State Machine

```
                 ┌──────────────────────────────────────────────┐
                 ▼                                              │ 
REQUESTED ──→ CONFIRMED ──→ IN_PROGRESS ──→ COMPLETED          │
    │              │                                            │
    └──────────────┴──────────────────────────────→ CANCELLED  ◄┘
    
Allowed transitions:
  REQUESTED   → CONFIRMED, CANCELLED
  CONFIRMED   → IN_PROGRESS, CANCELLED
  IN_PROGRESS → COMPLETED            (CANCELLED is FORBIDDEN from here)
  COMPLETED   → (terminal, no transitions)
  CANCELLED   → (terminal, no transitions)
```

---

## Testing Strategy

### Dual Testing Approach

Unit tests and property-based tests are complementary. Property tests (via **jqwik** for Java) verify universal invariants; unit tests cover specific examples, edge cases, and integration wiring.

**Property-Based Testing Library:** [jqwik](https://jqwik.net/) (Java property-based testing, JUnit 5 compatible).

Add to `pom.xml` test scope:
```xml
<dependency>
    <groupId>net.jqwik</groupId>
    <artifactId>jqwik</artifactId>
    <version>1.8.4</version>
    <scope>test</scope>
</dependency>
```

Each property test MUST run a minimum of **100 iterations** (jqwik default is 1000, so no override needed).

### Property Test Tagging

Each property test is tagged with a comment:
```
// Feature: airport-shuttle-service, Property N: <property text>
```

### Test File Layout

```
src/test/java/com/hotel/concierge/
  service/
    ShuttleBookingServiceTest.java         ← unit tests (Mockito)
    ShuttleBookingServicePropertyTest.java ← property tests (jqwik)
  controller/
    ShuttleBookingControllerTest.java      ← MockMvc unit tests
    ShuttleBookingControllerPropertyTest.java ← MockMvc property tests
  tools/
    ConciergeToolsShuttleTest.java         ← tool unit + property tests
```

### Unit Test Coverage (concrete examples)

- `ShuttleBookingServiceTest`: booking not found → throws `ShuttleBookingNotFoundException`; invalid status transition → throws `InvalidStatusTransitionException`; wrong ownership → throws `ShuttleBookingAccessDeniedException`; happy-path create, get, cancel, status update.
- `ShuttleBookingControllerTest`: missing required field → 400 with errors map; invalid status string → 400; non-existent booking ID → 404; cross-reservation cancel → 403.
- `ConciergeToolsShuttleTest`: tool with past date → error string, no DB record; tool with valid input → string contains `SHU-` pattern.

### Integration Tests (Testcontainers MySQL)

The existing `testcontainers/mysql` dependency supports full-stack slice tests:

- Create booking → verify DB record and HTTP 201.
- List by reservation → verify ordering.
- Admin hotel filter by status → verify no cross-status leakage.
- Cancel eligible booking → verify DB status = CANCELLED.
- Cancel ineligible (IN_PROGRESS) → verify HTTP 400.

### OpenAPI Smoke Test

A single test loads the Springdoc `/v3/api-docs` endpoint and verifies the `Airport Shuttle` tag is present with at least the POST and GET endpoints documented.
