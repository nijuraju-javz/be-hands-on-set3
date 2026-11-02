package com.edstem.bookingSlot.controller;

import com.edstem.bookingSlot.dto.BookingResponse;
import com.edstem.bookingSlot.dto.RoomResponse;
import com.edstem.bookingSlot.service.BookingService;
import com.edstem.bookingSlot.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;
    private final BookingService bookingService;

    @GetMapping
    public List<RoomResponse> getAllRooms() {
        return roomService.getAllRooms();
    }

    @GetMapping("/{id}")
    public RoomResponse getRoomById(@PathVariable Long id) {
        return roomService.getRoomResponseById(id);
    }

    @GetMapping("/{id}/bookings")
    public List<BookingResponse> getRoomBookings(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return bookingService.listRoomBookings(id, date);
    }
}
