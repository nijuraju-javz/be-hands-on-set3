package com.edstem.bookingSlot.controller;

import com.edstem.bookingSlot.dto.BookingResponse;
import com.edstem.bookingSlot.dto.CreateBookingRequest;
import com.edstem.bookingSlot.dto.FreeSlotResponse;
import com.edstem.bookingSlot.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping("/bookings")
    public ResponseEntity<BookingResponse> createBooking(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyHeader,
            @Valid @RequestBody CreateBookingRequest request
    ) {
        if (request.getIdempotencyKey() == null || request.getIdempotencyKey().isBlank()) {
            if (idempotencyHeader != null && !idempotencyHeader.isBlank()) {
                request.setIdempotencyKey(idempotencyHeader);
            }
        }
        BookingResponse response = bookingService.createBooking(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/bookings/{id}")
    public ResponseEntity<BookingResponse> getBooking(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.getBookingById(id));
    }

    @GetMapping("/bookings")
    public ResponseEntity<List<BookingResponse>> getBookings(
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        if (roomId != null && date != null) {
            return ResponseEntity.ok(bookingService.listRoomBookings(roomId, date));
        }
        return ResponseEntity.badRequest().build();
    }

    @DeleteMapping("/bookings/{id}")
    public ResponseEntity<BookingResponse> cancelBookingWithDelete(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.cancelBooking(id));
    }

    @PostMapping("/bookings/{id}/cancel")
    public ResponseEntity<BookingResponse> cancelBookingWithPost(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.cancelBooking(id));
    }

    @GetMapping("/free-slots")
    public ResponseEntity<List<FreeSlotResponse>> getFreeSlots(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(name = "duration", defaultValue = "30") int duration,
            @RequestParam List<String> attendees
    ) {
        return ResponseEntity.ok(bookingService.findFreeSlots(date, duration, attendees));
    }
}
