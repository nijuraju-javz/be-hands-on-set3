package com.edstem.bookingSlot.service;

import com.edstem.bookingSlot.dto.BookingResponse;
import com.edstem.bookingSlot.dto.CreateBookingRequest;
import com.edstem.bookingSlot.dto.FreeSlotResponse;
import com.edstem.bookingSlot.dto.RoomResponse;
import com.edstem.bookingSlot.exception.BookingOverlapException;
import com.edstem.bookingSlot.exception.ResourceNotFoundException;
import com.edstem.bookingSlot.exception.ValidationException;
import com.edstem.bookingSlot.model.Booking;
import com.edstem.bookingSlot.model.BookingStatus;
import com.edstem.bookingSlot.model.Room;
import com.edstem.bookingSlot.repository.BookingRepository;
import com.edstem.bookingSlot.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;
    private final Clock clock;

    private static final LocalTime WORKING_HOURS_START = LocalTime.of(9, 0);
    private static final LocalTime WORKING_HOURS_END = LocalTime.of(18, 0);

    @Transactional
    public BookingResponse createBooking(CreateBookingRequest request) {
        // Safe retry check via idempotency key
        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            Optional<Booking> existing = bookingRepository.findByIdempotencyKey(request.getIdempotencyKey());
            if (existing.isPresent()) {
                return BookingResponse.fromEntity(existing.get());
            }
        }

        // Lock the room row to ensure strict database-level concurrency protection
        Room room = roomRepository.findByIdForUpdate(request.getRoomId())
                .orElseThrow(() -> new ResourceNotFoundException("Room not found with ID: " + request.getRoomId()));

        Map<String, String> fieldErrors = new HashMap<>();

        if (!room.isActive()) {
            fieldErrors.put("roomId", "Room is inactive and cannot be booked");
        }

        LocalDateTime startTime = request.getStartTime();
        LocalDateTime endTime = request.getEndTime();

        if (startTime != null && endTime != null) {
            // Must be on a single day
            if (!startTime.toLocalDate().equals(endTime.toLocalDate())) {
                fieldErrors.put("endTime", "Booking must be on a single day");
            }

            // Start before end
            if (!startTime.isBefore(endTime)) {
                fieldErrors.put("startTime", "Booking start time must be before end time");
            }

            // Within working hours 09:00 to 18:00
            LocalTime startLocalTime = startTime.toLocalTime();
            LocalTime endLocalTime = endTime.toLocalTime();
            if (startLocalTime.isBefore(WORKING_HOURS_START) || startLocalTime.isAfter(WORKING_HOURS_END)) {
                fieldErrors.put("startTime", "Booking start time must be within working hours (09:00 to 18:00)");
            }
            if (endLocalTime.isBefore(WORKING_HOURS_START) || endLocalTime.isAfter(WORKING_HOURS_END)) {
                fieldErrors.put("endTime", "Booking end time must be within working hours (09:00 to 18:00)");
            }

            // 15-minute steps
            if (startTime.getMinute() % 15 != 0 || startTime.getSecond() != 0 || startTime.getNano() != 0) {
                fieldErrors.put("startTime", "Booking start time must align with 15-minute intervals");
            }
            if (endTime.getMinute() % 15 != 0 || endTime.getSecond() != 0 || endTime.getNano() != 0) {
                fieldErrors.put("endTime", "Booking end time must align with 15-minute intervals");
            }

            // Last at most 4 hours
            long durationMinutes = Duration.between(startTime, endTime).toMinutes();
            if (durationMinutes > 240) {
                fieldErrors.put("duration", "Booking cannot exceed 4 hours");
            }

            // Not be in the past
            if (startTime.isBefore(LocalDateTime.now(clock))) {
                fieldErrors.put("startTime", "Booking cannot be in the past");
            }
        }

        // Attendees capacity check (organiser is always an attendee)
        Set<String> attendeeSet = new LinkedHashSet<>();
        if (request.getAttendees() != null) {
            attendeeSet.addAll(request.getAttendees());
        }
        if (request.getOrganizerEmail() != null && !request.getOrganizerEmail().isBlank()) {
            attendeeSet.add(request.getOrganizerEmail());
        }

        if (attendeeSet.size() > room.getCapacity()) {
            fieldErrors.put("attendees", "Number of attendees (" + attendeeSet.size() + ") exceeds room capacity (" + room.getCapacity() + ")");
        }

        if (!fieldErrors.isEmpty()) {
            throw new ValidationException(fieldErrors);
        }

        // Check for overlapping bookings in the same room
        List<Booking> overlapping = bookingRepository.findOverlappingActiveBookings(room.getId(), startTime, endTime);
        if (!overlapping.isEmpty()) {
            Booking clash = overlapping.get(0);
            throw new BookingOverlapException("Booking clashes with existing booking ID: " + clash.getId() + " ('" + clash.getTitle() + "' from " + clash.getStartTime() + " to " + clash.getEndTime() + ")", clash.getId());
        }

        Booking booking = Booking.builder()
                .room(room)
                .title(request.getTitle())
                .organizerEmail(request.getOrganizerEmail())
                .attendees(new ArrayList<>(attendeeSet))
                .startTime(startTime)
                .endTime(endTime)
                .status(BookingStatus.ACTIVE)
                .createdAt(LocalDateTime.now(clock))
                .idempotencyKey(request.getIdempotencyKey())
                .build();

        Booking saved = bookingRepository.save(booking);
        return BookingResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public BookingResponse getBookingById(Long id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with ID: " + id));
        return BookingResponse.fromEntity(booking);
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> listRoomBookings(Long roomId, LocalDate date) {
        roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found with ID: " + roomId));

        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();
        return bookingRepository.findByRoomIdAndDateRange(roomId, dayStart, dayEnd).stream()
                .map(BookingResponse::fromEntity)
                .toList();
    }

    @Transactional
    public BookingResponse cancelBooking(Long id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with ID: " + id));
        booking.setStatus(BookingStatus.CANCELLED);
        Booking updated = bookingRepository.save(booking);
        return BookingResponse.fromEntity(updated);
    }

    @Transactional(readOnly = true)
    public List<FreeSlotResponse> findFreeSlots(LocalDate date, int minDurationMinutes, List<String> attendeeEmails) {
        if (minDurationMinutes <= 0) {
            throw new ValidationException("duration", "Minimum duration must be greater than 0");
        }
        if (attendeeEmails == null || attendeeEmails.isEmpty()) {
            throw new ValidationException("attendees", "At least one attendee email is required");
        }

        List<String> distinctAttendees = attendeeEmails.stream()
                .filter(email -> email != null && !email.isBlank())
                .map(String::trim)
                .distinct()
                .toList();

        if (distinctAttendees.isEmpty()) {
            throw new ValidationException("attendees", "At least one valid attendee email is required");
        }

        LocalDateTime dayWorkStart = date.atTime(WORKING_HOURS_START);
        LocalDateTime dayWorkEnd = date.atTime(WORKING_HOURS_END);

        List<Booking> activeBookingsOnDate = bookingRepository.findActiveBookingsOnDate(
                date.atStartOfDay(),
                date.plusDays(1).atStartOfDay()
        );

        // Fetch non-cancelled bookings where any of the attendees is organizer or attendee
        Set<String> targetAttendeeSet = new HashSet<>(distinctAttendees);
        List<Booking> attendeeBookings = activeBookingsOnDate.stream()
                .filter(b -> targetAttendeeSet.contains(b.getOrganizerEmail()) ||
                        (b.getAttendees() != null && b.getAttendees().stream().anyMatch(targetAttendeeSet::contains)))
                .toList();

        // Clip busy intervals to working hours
        List<TimeInterval> busyIntervals = new ArrayList<>();
        for (Booking b : attendeeBookings) {
            LocalDateTime bStart = b.getStartTime().isBefore(dayWorkStart) ? dayWorkStart : b.getStartTime();
            LocalDateTime bEnd = b.getEndTime().isAfter(dayWorkEnd) ? dayWorkEnd : b.getEndTime();
            if (bStart.isBefore(bEnd)) {
                busyIntervals.add(new TimeInterval(bStart, bEnd));
            }
        }

        // Sort busy intervals by start time
        busyIntervals.sort(Comparator.comparing(TimeInterval::start));

        // Merge overlapping/touching busy intervals
        List<TimeInterval> mergedBusy = new ArrayList<>();
        for (TimeInterval interval : busyIntervals) {
            if (mergedBusy.isEmpty()) {
                mergedBusy.add(interval);
            } else {
                TimeInterval last = mergedBusy.get(mergedBusy.size() - 1);
                if (!interval.start().isAfter(last.end())) {
                    LocalDateTime newEnd = interval.end().isAfter(last.end()) ? interval.end() : last.end();
                    mergedBusy.set(mergedBusy.size() - 1, new TimeInterval(last.start(), newEnd));
                } else {
                    mergedBusy.add(interval);
                }
            }
        }

        // Find free slots
        List<TimeInterval> freeIntervals = new ArrayList<>();
        LocalDateTime cursor = dayWorkStart;
        for (TimeInterval busy : mergedBusy) {
            if (busy.start().isAfter(cursor)) {
                long duration = Duration.between(cursor, busy.start()).toMinutes();
                if (duration >= minDurationMinutes) {
                    freeIntervals.add(new TimeInterval(cursor, busy.start()));
                }
            }
            if (busy.end().isAfter(cursor)) {
                cursor = busy.end();
            }
        }
        if (dayWorkEnd.isAfter(cursor)) {
            long duration = Duration.between(cursor, dayWorkEnd).toMinutes();
            if (duration >= minDurationMinutes) {
                freeIntervals.add(new TimeInterval(cursor, dayWorkEnd));
            }
        }

        // "Where to meet: For each returned slot, list the active rooms that are free for the whole slot and big enough for the group."
        int groupSize = distinctAttendees.size();
        List<Room> activeCandidateRooms = roomRepository.findByActiveTrue().stream()
                .filter(room -> room.getCapacity() >= groupSize)
                .toList();

        List<FreeSlotResponse> results = new ArrayList<>();
        for (TimeInterval slot : freeIntervals) {
            List<RoomResponse> availableRooms = new ArrayList<>();
            for (Room room : activeCandidateRooms) {
                boolean roomBusy = activeBookingsOnDate.stream().anyMatch(b ->
                        b.getRoom().getId().equals(room.getId())
                                && b.getStartTime().isBefore(slot.end())
                                && b.getEndTime().isAfter(slot.start())
                );
                if (!roomBusy) {
                    availableRooms.add(RoomResponse.fromEntity(room));
                }
            }
            results.add(FreeSlotResponse.builder()
                    .startTime(slot.start())
                    .endTime(slot.end())
                    .availableRooms(availableRooms)
                    .build());
        }

        return results;
    }

    private record TimeInterval(LocalDateTime start, LocalDateTime end) {}
}
