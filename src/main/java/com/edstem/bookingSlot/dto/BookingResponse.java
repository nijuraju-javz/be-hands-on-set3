package com.edstem.bookingSlot.dto;

import com.edstem.bookingSlot.model.Booking;
import com.edstem.bookingSlot.model.BookingStatus;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingResponse {

    private Long id;
    private Long roomId;
    private String roomName;
    private String title;
    private String organizerEmail;
    private List<String> attendees;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BookingStatus status;
    private LocalDateTime createdAt;

    public static BookingResponse fromEntity(Booking booking) {
        return BookingResponse.builder()
                .id(booking.getId())
                .roomId(booking.getRoom() != null ? booking.getRoom().getId() : null)
                .roomName(booking.getRoom() != null ? booking.getRoom().getName() : null)
                .title(booking.getTitle())
                .organizerEmail(booking.getOrganizerEmail())
                .attendees(booking.getAttendees() != null ? new ArrayList<>(booking.getAttendees()) : new ArrayList<>())
                .startTime(booking.getStartTime())
                .endTime(booking.getEndTime())
                .status(booking.getStatus())
                .createdAt(booking.getCreatedAt())
                .build();
    }
}
