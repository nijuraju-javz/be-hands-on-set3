package com.edstem.bookingSlot.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "bookings", indexes = {
        @Index(name = "idx_booking_room_time", columnList = "room_id, start_time, end_time, status"),
        @Index(name = "idx_booking_idempotency", columnList = "idempotency_key")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(nullable = false)
    private String title;

    @Column(name = "organizer_email", nullable = false)
    private String organizerEmail;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "booking_attendees", joinColumns = @JoinColumn(name = "booking_id"))
    @Column(name = "attendee_email", nullable = false)
    @Builder.Default
    private List<String> attendees = new ArrayList<>();

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;
}
