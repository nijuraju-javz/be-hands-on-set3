package com.edstem.bookingSlot.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateBookingRequest {

    @NotNull(message = "Room ID is required")
    private Long roomId;

    @NotBlank(message = "Title is required")
    private String title;

    @NotBlank(message = "Organizer email is required")
    @Email(message = "Organizer email must be valid")
    private String organizerEmail;

    @Builder.Default
    private List<@NotBlank(message = "Attendee email must not be blank") @Email(message = "Attendee email must be valid") String> attendees = new ArrayList<>();

    @NotNull(message = "Start time is required")
    private LocalDateTime startTime;

    @NotNull(message = "End time is required")
    private LocalDateTime endTime;

    private String idempotencyKey;
}
