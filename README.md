# Meeting Room Booking Service (`be-hands-on-set3`)

A production-grade meeting room booking service built with **Java 21** and **Spring Boot 3.3.0**.

---

## Question PR Links & Summary

| # | Question | PR Link | Description |
|---|---|---|---|
| 1 | Room Booking API | [PR #1: Room Booking API](https://github.com/nijuraju-javz/be-hands-on-set3/pull/1) | Room reservation API with business rules validation, clash detection, and cancellation. |
| 2 | Free Slots & Safe Booking | [PR #2: Free Slots & Safe Booking](https://github.com/nijuraju-javz/be-hands-on-set3/pull/2) | Common attendee free slots, room recommendation, pessimistic locking concurrency control, and idempotent retries. |

---

## Pull Request Details

### PR 1: Room Booking API (`feature/q1-room-booking`)

#### Problem
Provides a RESTful API for meeting room bookings with strict schedule validation, 15-minute slot alignment, working hours enforcement (09:00–18:00), capacity checks, overlap rejection with clashing booking details, and room cancellation.

#### Approach
- **`Room` & `Booking` Entities**: JPA entities mapped with indexes on `(room_id, start_time, end_time, status)`.
- **`RoomDataInitializer`**: Seeds 5 active rooms on startup (`Focus Room`, `Team Room Alpha`, `Team Room Beta`, `Boardroom`, `Workshop Hall`).
- **`BookingService`**: Validates business invariants:
  - Single day constraint
  - Working hours: 09:00 to 18:00
  - Start before end
  - 15-minute interval alignment
  - Max duration: 4 hours
  - Not in the past
  - Organizer auto-included in attendees and group size <= room capacity
  - Overlap detection: non-cancelled bookings in the same room cannot overlap `[start, end)`. Back-to-back bookings (e.g. 10:00–11:00 and 11:00–12:00) are permitted.
- **`GlobalExceptionHandler`**: Generates uniform error JSON with HTTP 400 (per-field validation errors), 404 (not found), and 409 (naming clashing booking ID).

#### Decisions & Trade-offs
- **JPA & H2 Database**: Fast in-memory persistence with ACID transaction boundaries and zero external dependencies for evaluation.
- **Structured Error Format**: Consistent schema across all error types with field-level granularity (`fieldErrors`) and clash identification (`clashingBookingId`).

#### How to Test
- Test class: `com.edstem.bookingSlot.RoomBookingApiTests`
- Run: `./mvnw test -Dtest=RoomBookingApiTests`

---

### PR 2: Free Slots & Safe Booking (`feature/q2-free-slots`)

#### Problem
Finds common free slots across multiple attendees within working hours, recommends suitable active rooms for the group, prevents double-booking under heavy concurrent load, and supports safe request retries.

#### Approach
- **Common Free Slots Algorithm**:
  1. Gathers all non-cancelled bookings for requested attendees on the given date across all rooms.
  2. Clips intervals to working hours (09:00–18:00).
  3. Merges overlapping and contiguous busy intervals.
  4. Takes the complement of busy intervals against `[09:00, 18:00]`.
  5. Filters slots with duration >= `D` minutes.
  6. Filters active candidate rooms with `capacity >= groupSize` that are free for the entire slot duration.
- **Concurrency Control**:
  - Uses `@Lock(LockModeType.PESSIMISTIC_WRITE)` (`SELECT ... FOR UPDATE`) on the `Room` row inside `@Transactional`.
  - Serializes concurrent booking attempts for the same room at the database level, ensuring safety even across multiple running application instances.
- **Safe Retries**:
  - Supports `Idempotency-Key` header or `idempotencyKey` field in request body.
  - Matches previous successful booking and returns existing record without creating duplicates.

#### Decisions & Trade-offs
- **Database Row Lock vs In-Memory Lock**: Database-level pessimistic locking (`PESSIMISTIC_WRITE`) guarantees correctness across multiple application replicas pointing to the same database.
- **Idempotency Key**: Uses unique key constraint to guarantee exactly-once booking semantics over flaky networks.

#### How to Test
- Test class: `com.edstem.bookingSlot.FreeSlotsAndSafeBookingTests`
- Run: `./mvnw test -Dtest=FreeSlotsAndSafeBookingTests`

---

## How to Run the Application

### Prerequisites
- JDK 21+
- Git

### Build and Run
```bash
./mvnw clean spring-boot:run
```
The application starts on `http://localhost:8080`.

---

## How to Run the Tests
Run the complete automated test suite:
```bash
./mvnw test
```

---

## API Endpoints Reference

### 1. Create a Booking
- **POST** `/api/bookings`
- **Headers**: `Idempotency-Key: <optional-key>`
- **Request Body**:
```json
{
  "roomId": 1,
  "title": "Architecture Sync",
  "organizerEmail": "alice@example.com",
  "attendees": ["bob@example.com"],
  "startTime": "2026-11-10T10:00:00",
  "endTime": "2026-11-10T11:00:00",
  "idempotencyKey": "uuid-1234"
}
```
- **Responses**:
  - `201 Created`: Booking created successfully.
  - `400 Bad Request`: Validation error with `fieldErrors`.
  - `404 Not Found`: Room not found.
  - `409 Conflict`: Overlapping booking with `clashingBookingId`.

### 2. Get Booking by ID
- **GET** `/api/bookings/{id}`
- **Responses**: `200 OK`, `404 Not Found`.

### 3. Cancel Booking
- **DELETE** `/api/bookings/{id}` or **POST** `/api/bookings/{id}/cancel`
- **Responses**: `200 OK` (sets status to `CANCELLED`; frees room).

### 4. List Room Bookings for a Date
- **GET** `/api/rooms/{id}/bookings?date=2026-11-10`
- **Responses**: `200 OK` (list in chronological order).

### 5. Find Free Slots
- **GET** `/api/free-slots?date=2026-11-10&duration=30&attendees=alice@example.com&attendees=bob@example.com`
- **Responses**: `200 OK` (returns slots >= duration with available candidate rooms).
