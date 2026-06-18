# Airport Shuttle Service — Feature Documentation

This document describes the design, business logic, data model, API endpoints, and AI tool integration for the Airport Shuttle Service feature added to the AI Concierge platform.

---

## Table of Contents

1. [Overview](#1-overview)
2. [Database Schema](#2-database-schema)
3. [Business Logic & Flows](#3-business-logic--flows)
4. [AI Tool Integration](#4-ai-tool-integration)
5. [API Endpoints](#5-api-endpoints)
6. [Status Lifecycle](#6-status-lifecycle)
7. [Error Handling](#7-error-handling)
8. [UI Integration](#8-ui-integration)
9. [Property-Based Correctness Properties](#9-property-based-correctness-properties)

---

## 1. Overview

The Airport Shuttle Service allows hotel guests to book and cancel airport transportation via the AI concierge chat interface. Hotel admins can view all shuttle bookings for their hotel, filter by status or pickup date, and update booking statuses through the admin dashboard.

### Key Capabilities

- Guests book shuttle pickups/dropoffs via AI chat (natural language)
- Guests cancel their own bookings by reference number via AI chat
- Admins view all hotel shuttle bookings via dashboard panel
- Admins update booking status via REST API
- Full lifecycle: `REQUESTED → CONFIRMED → IN_PROGRESS → COMPLETED` (or `CANCELLED`)

### Components Added

| Layer | File | Purpose |
|-------|------|---------|
| Entity | `model/ShuttleBooking.java` | JPA entity + `BookingStatus` enum |
| Repository | `repository/ShuttleBookingRepository.java` | Data access with hotel-scoped JPQL |
| Service | `service/ShuttleBookingService.java` | Business logic + validation |
| Controller | `controller/ShuttleBookingController.java` | REST endpoints |
| DTOs | `dto/ShuttleBookingRequest.java`, `dto/ShuttleBookingResponse.java` | API contracts |
| Exceptions | `exception/ShuttleBookingNotFoundException.java`, `ShuttleBookingAccessDeniedException.java`, `InvalidStatusTransitionException.java` | Domain errors |
| AI Tools | `service/ai/tools/ConciergeTools.java` | `bookAirportShuttle` + `cancelAirportShuttle` |
| Migration | `resources/db/shuttle-bookings-migration.sql` | Schema DDL |

---

## 2. Database Schema

### Table: `shuttle_bookings`

```sql
CREATE TABLE IF NOT EXISTS shuttle_bookings (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    reservation_id       BIGINT        NOT NULL,
    booking_reference    VARCHAR(50)   NOT NULL UNIQUE,
    pickup_location      VARCHAR(500)  NOT NULL,
    dropoff_location     VARCHAR(500)  NOT NULL,
    pickup_datetime      DATETIME      NOT NULL,
    passenger_count      INT           NOT NULL,
    contact_phone        VARCHAR(30)   NOT NULL,
    status               VARCHAR(30)   NOT NULL DEFAULT 'REQUESTED',
    special_instructions VARCHAR(1000),
    created_at           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_shuttle_reservation FOREIGN KEY (reservation_id) REFERENCES reservations(id),
    CONSTRAINT uq_shuttle_reference   UNIQUE (booking_reference),
    CONSTRAINT chk_passenger_count    CHECK (passenger_count >= 1),

    INDEX idx_shuttle_reservation  (reservation_id),
    INDEX idx_shuttle_status       (status),
    INDEX idx_shuttle_pickup       (pickup_datetime)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### Booking Reference Format

Each booking gets a unique reference generated at creation time:

```
SHU-{reservationId}-{sequenceNumber}
```

Examples: `SHU-1-001`, `SHU-1-002`, `SHU-42-003`

The sequence number is derived from counting existing bookings on the same reservation (`countByReservationId + 1`), zero-padded to 3 digits.

### BookingStatus Enum

```java
enum BookingStatus {
    REQUESTED,    // Initial state on creation
    CONFIRMED,    // Confirmed by staff
    IN_PROGRESS,  // Pickup underway
    COMPLETED,    // Journey completed
    CANCELLED     // Cancelled by guest or staff
}
```

---

## 3. Business Logic & Flows

### 3.1 Guest Books a Shuttle (via AI Chat)

```
Guest: "I need a shuttle to the airport tomorrow at 9am"
    → LLM calls bookAirportShuttle(
          reservationId, pickupLocation, dropoffLocation,
          pickupDate, pickupTime, passengerCount, contactPhone, specialInstructions
      )
        1. Parse pickupDate + "T" + pickupTime → LocalDateTime
        2. Validate pickupDatetime is in the future — return error string if past
        3. Validate passengerCount in [1, 10] — return error string if out of range
        4. Load Reservation by reservationId
        5. Generate bookingReference: "SHU-{id}-{count+1}"
        6. Build and save ShuttleBooking (status = REQUESTED)
        7. Return confirmation string containing bookingReference
    → LLM presents confirmation to guest with reference number
```

### 3.2 Guest Cancels a Shuttle (via AI Chat)

```
Guest: "Cancel my shuttle booking SHU-1-001"
    → LLM calls cancelAirportShuttle(bookingReference)
        1. Find booking by bookingReference → return "Booking not found" if absent
        2. Check status is REQUESTED or CONFIRMED
           → Return "Cannot cancel a booking in status {status}" if not eligible
        3. Set status = CANCELLED, save
        4. Return cancellation confirmation string
```

### 3.3 Guest Retrieves Their Bookings (REST)

```
GET /shuttle-bookings/reservation/{reservationId}
    → ShuttleBookingService.getBookingsByReservation(reservationId)
        1. findByReservationIdOrderByPickupDatetimeAsc(reservationId)
        2. Map each ShuttleBooking → ShuttleBookingResponse
        3. Return list sorted ascending by pickupDatetime
```

### 3.4 Admin Views Hotel Shuttle Bookings

```
GET /admin/shuttle-bookings/hotel/{hotelId}?status=CONFIRMED&pickupDate=2026-06-01
    → ShuttleBookingService.getBookingsByHotel(hotelId, status, pickupDate)
        1. Execute JPQL query with optional filters:
           - JOIN reservation → hotel (hotel_id = :hotelId)
           - WHERE status = :status (if provided)
           - WHERE DATE(pickup_datetime) = :pickupDate (if provided)
        2. Map → ShuttleBookingResponse list
```

### 3.5 Admin Updates Booking Status

```
PUT /admin/shuttle-bookings/{id}/status?status=CONFIRMED
    → ShuttleBookingService.updateStatus(bookingId, newStatus)
        1. Load booking — throw ShuttleBookingNotFoundException if absent
        2. Validate transition using allowed transitions map (see §6)
           — throw InvalidStatusTransitionException if forbidden
        3. Set new status, save, return updated ShuttleBookingResponse
```

### 3.6 Guest Cancels via REST

```
DELETE /shuttle-bookings/{id}?reservationId={reservationId}
    → ShuttleBookingService.cancelBooking(bookingId, reservationId)
        1. Load booking — throw ShuttleBookingNotFoundException if absent
        2. Check booking.reservation.id == reservationId
           — throw ShuttleBookingAccessDeniedException if mismatch
        3. Check status is REQUESTED or CONFIRMED
           — throw InvalidStatusTransitionException if not
        4. Set status = CANCELLED, save, return response
```

---

## 4. AI Tool Integration

### Tools Added to `ConciergeTools.java`

#### `bookAirportShuttle`

```
@Tool("Book an airport shuttle for the guest")
String bookAirportShuttle(
    String pickupLocation,
    String dropoffLocation,
    String pickupDate,       // ISO date: "2026-06-01"
    String pickupTime,       // 24h time: "09:00"
    int passengerCount,
    String contactPhone,
    String specialInstructions
)
```

**Validation (no DB write on failure):**
- `pickupDatetime` must be in the future
- `passengerCount` must be between 1 and 10

**On success:** saves `ShuttleBooking` with status `REQUESTED`, returns confirmation string with booking reference.

#### `cancelAirportShuttle`

```
@Tool("Cancel an airport shuttle booking by reference number")
String cancelAirportShuttle(String bookingReference)
```

**Validation:**
- Booking must exist by reference
- Status must be `REQUESTED` or `CONFIRMED`

**On success:** sets status to `CANCELLED`, saves, returns confirmation string.

### Factory Update (`ConciergeToolsFactory.java`)

`ShuttleBookingRepository` is injected and passed to the `ConciergeTools` constructor so the per-session tool instance has access to shuttle data bound to the guest's reservation.

### System Prompt Update (`ConciergeAiService.java`)

The `buildSystemPrompt` method includes the two new shuttle tools in the **AVAILABLE TOOLS** section:

```
- bookAirportShuttle: Book a shuttle to/from the airport
- cancelAirportShuttle: Cancel an existing shuttle booking by reference
```

And the guest-facing welcome message quick actions list includes:
```
✈️ Airport shuttle transportation
```

---

## 5. API Endpoints

### Guest Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/shuttle-bookings` | Create a new shuttle booking |
| `GET` | `/shuttle-bookings/{id}` | Get a single booking by ID |
| `GET` | `/shuttle-bookings/reservation/{reservationId}` | List all bookings for a reservation (sorted by pickup time ASC) |
| `DELETE` | `/shuttle-bookings/{id}?reservationId={reservationId}` | Cancel a booking (ownership check enforced) |

### Admin Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/admin/shuttle-bookings/hotel/{hotelId}` | List all hotel shuttle bookings (optional filters: `status`, `pickupDate`) |
| `PUT` | `/admin/shuttle-bookings/{id}/status?status={status}` | Update booking status (enforces valid transitions) |

### Request Body: `ShuttleBookingRequest`

```json
{
  "pickupLocation":      "The Grand Meridian Hotel, 5th Ave, New York",
  "dropoffLocation":     "JFK International Airport, Terminal 4",
  "pickupDatetime":      "2026-06-10T09:00:00",
  "passengerCount":      2,
  "contactPhone":        "+1-212-555-0100",
  "specialInstructions": "Extra luggage, please bring large vehicle"
}
```

**Validation rules:**
- `pickupLocation` — required, not blank
- `dropoffLocation` — required, not blank
- `pickupDatetime` — required, must be in the future
- `passengerCount` — required, min 1, max 10
- `contactPhone` — required, not blank
- `specialInstructions` — optional

### Response Body: `ShuttleBookingResponse`

```json
{
  "id":                   42,
  "bookingReference":     "SHU-1-003",
  "pickupLocation":       "The Grand Meridian Hotel, 5th Ave, New York",
  "dropoffLocation":      "JFK International Airport, Terminal 4",
  "pickupDatetime":       "2026-06-10T09:00:00",
  "passengerCount":       2,
  "contactPhone":         "+1-212-555-0100",
  "status":               "REQUESTED",
  "specialInstructions":  "Extra luggage, please bring large vehicle",
  "reservationId":        1,
  "createdAt":            "2026-06-02T10:15:00",
  "updatedAt":            "2026-06-02T10:15:00"
}
```

---

## 6. Status Lifecycle

### Allowed Transitions

```
REQUESTED  ──→  CONFIRMED
REQUESTED  ──→  CANCELLED
CONFIRMED  ──→  IN_PROGRESS
CONFIRMED  ──→  CANCELLED
IN_PROGRESS ──→ COMPLETED
```

### Forbidden Transitions (throw `InvalidStatusTransitionException`)

- `COMPLETED → *` (any)
- `CANCELLED → *` (any)
- `IN_PROGRESS → CANCELLED`
- Any other transition not listed above

### Lifecycle Diagram

```
  [Guest Books]
       │
       ▼
  REQUESTED ──────────────────────→ CANCELLED
       │                               ▲
       ▼                               │ (guest or admin, only from REQUESTED/CONFIRMED)
  CONFIRMED ──────────────────────→ CANCELLED
       │
       ▼
  IN_PROGRESS
       │
       ▼
  COMPLETED
```

---

## 7. Error Handling

### Exception Types

| Exception | HTTP Status | When Thrown |
|-----------|-------------|-------------|
| `ShuttleBookingNotFoundException` | 404 | Booking ID or reference not found |
| `ShuttleBookingAccessDeniedException` | 403 | Guest tries to cancel another guest's booking |
| `InvalidStatusTransitionException` | 400 | Illegal status transition attempted |
| `IllegalArgumentException` | 400 | Past pickup time or invalid passenger count (service layer) |
| `@Valid` violation | 400 | Bean validation fails on request body |
| `RuntimeException("Reservation not found")` | 500* | Reservation not found by ID |

*Mapped to 500 via GlobalExceptionHandler default handler. In production, this should be a dedicated 404 exception.

### GlobalExceptionHandler Entries Added

```java
@ExceptionHandler(ShuttleBookingNotFoundException.class)
// → 404 NOT FOUND

@ExceptionHandler(ShuttleBookingAccessDeniedException.class)
// → 403 FORBIDDEN
```

---

## 8. UI Integration

### Chat Quick Action Button

A new quick action button was added to the chat interface alongside Housekeeping, Spa, Restaurant, Late Checkout, and Attractions:

```html
<button class="quick-btn" data-action="shuttle">
    <i class="fas fa-plane"></i> Shuttle
</button>
```

**Clicking "Shuttle" sends:** `"I need to book an airport shuttle. Can you help me?"`

The AI responds by asking for pickup/dropoff locations, date, time, and passenger count, then calls `bookAirportShuttle` tool.

### Admin Dashboard Panel

A new panel was added to the dashboard grid after Restaurant Bookings:

```html
<div class="dash-panel glass-card">
    <div class="panel-header">
        <h3><i class="fas fa-plane"></i> Airport Shuttle Bookings</h3>
    </div>
    <div class="panel-content" id="shuttle-bookings-list">
        <!-- Populated dynamically -->
    </div>
</div>
```

**Each booking card shows:**
- Pickup → Dropoff locations
- Room number, formatted pickup date/time, passenger count
- Status and booking reference

### JavaScript Functions

```javascript
// Called from loadDashboardData() alongside other booking panels
async function loadShuttleBookings() {
    const response = await fetch(`/admin/shuttle-bookings/hotel/1`);
    // renders booking cards into #shuttle-bookings-list
}
```

**Demo mode response** added to `getDemoResponse()` — triggered by keywords `shuttle`, `airport`, `transportation`:

```
"Our premium shuttle service offers:
 • Shared Shuttle - $35 per person
 • Private Shuttle - $120 flat rate
 • Limousine Service - $150..."
```

---

## 9. Property-Based Correctness Properties

The following correctness properties are verified by jqwik property tests in `ShuttleBookingServicePropertyTest.java`, `ShuttleBookingControllerPropertyTest.java`, and `ConciergeToolsShuttleTest.java`:

| # | Property | Test Class |
|---|----------|------------|
| 1 | Valid booking always produces status `REQUESTED` | `ServicePropertyTest` |
| 2 | Booking references follow `SHU-\d+-\d+` pattern and are unique per reservation | `ServicePropertyTest` |
| 3 | HTTP 201 returned for any valid booking request | `ControllerPropertyTest` |
| 4 | Past pickup datetime is always rejected | `ServicePropertyTest` |
| 5 | `specialInstructions` round-trips exactly | `ServicePropertyTest` |
| 6 | Booking list is sorted ascending by `pickupDatetime` | `ControllerPropertyTest` |
| 7 | Create-then-retrieve returns identical fields | `IntegrationTest` |
| 8 | Hotel filter returns no cross-hotel results | `IntegrationTest` |
| 9 | Valid status transitions always succeed | `ServicePropertyTest` |
| 10 | Invalid status transitions always throw | `ServicePropertyTest` |
| 11 | Eligible cancellations transition to `CANCELLED` | `ServicePropertyTest` |
| 12 | Ownership mismatch always throws `ShuttleBookingAccessDeniedException` | `ServicePropertyTest` |
| 13 | AI tool confirmation string always contains booking reference | `ConciergeToolsShuttleTest` |
| 14 | AI tool error path produces no DB write | `ConciergeToolsShuttleTest` |
| 15 | Missing required fields always produce HTTP 400 with field map | `ControllerPropertyTest` |
| 16 | `createdAt` non-null on create; `updatedAt` advances on update | `ServicePropertyTest` |
