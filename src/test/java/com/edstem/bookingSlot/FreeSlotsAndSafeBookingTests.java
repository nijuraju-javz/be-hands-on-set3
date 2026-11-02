package com.edstem.bookingSlot;

import com.edstem.bookingSlot.dto.BookingResponse;
import com.edstem.bookingSlot.dto.CreateBookingRequest;
import com.edstem.bookingSlot.dto.FreeSlotResponse;
import com.edstem.bookingSlot.model.Booking;
import com.edstem.bookingSlot.model.BookingStatus;
import com.edstem.bookingSlot.model.Room;
import com.edstem.bookingSlot.repository.BookingRepository;
import com.edstem.bookingSlot.repository.RoomRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class FreeSlotsAndSafeBookingTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private BookingRepository bookingRepository;

    private LocalDate targetDate;
    private Room roomA;
    private Room roomB;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        targetDate = LocalDate.now().plusDays(3);

        List<Room> rooms = roomRepository.findAll();
        if (rooms.size() < 2) {
            roomA = roomRepository.save(Room.builder().name("Room Alpha").capacity(4).active(true).build());
            roomB = roomRepository.save(Room.builder().name("Room Beta").capacity(6).active(true).build());
        } else {
            roomA = rooms.get(0);
            roomB = rooms.get(1);
        }
    }

    @Test
    @DisplayName("Acceptance Criteria: Free slots calculation matches exact specification for A and B with D=30")
    void testAcceptanceCriteriaCommonFreeSlots() throws Exception {
        String attendeeA = "attendeeA@example.com";
        String attendeeB = "attendeeB@example.com";

        // Attendee A has bookings 10:00–11:00 and 09:30–10:30 (in any rooms)
        bookingRepository.save(Booking.builder()
                .room(roomA)
                .title("Meeting A1")
                .organizerEmail(attendeeA)
                .attendees(List.of(attendeeA))
                .startTime(targetDate.atTime(10, 0))
                .endTime(targetDate.atTime(11, 0))
                .status(BookingStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build());

        bookingRepository.save(Booking.builder()
                .room(roomB)
                .title("Meeting A2")
                .organizerEmail("colleague1@example.com")
                .attendees(List.of("colleague1@example.com", attendeeA))
                .startTime(targetDate.atTime(9, 30))
                .endTime(targetDate.atTime(10, 30))
                .status(BookingStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build());

        // Attendee B has bookings 13:00–14:00 and 13:15–13:45 (in any rooms)
        bookingRepository.save(Booking.builder()
                .room(roomA)
                .title("Meeting B1")
                .organizerEmail(attendeeB)
                .attendees(List.of(attendeeB))
                .startTime(targetDate.atTime(13, 0))
                .endTime(targetDate.atTime(14, 0))
                .status(BookingStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build());

        bookingRepository.save(Booking.builder()
                .room(roomB)
                .title("Meeting B2")
                .organizerEmail("colleague2@example.com")
                .attendees(List.of("colleague2@example.com", attendeeB))
                .startTime(targetDate.atTime(13, 15))
                .endTime(targetDate.atTime(13, 45))
                .status(BookingStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build());

        // Also add a CANCELLED booking during 11:30-12:30 to verify cancelled bookings do not block slots
        bookingRepository.save(Booking.builder()
                .room(roomA)
                .title("Cancelled Meeting")
                .organizerEmail(attendeeA)
                .attendees(List.of(attendeeA))
                .startTime(targetDate.atTime(11, 30))
                .endTime(targetDate.atTime(12, 30))
                .status(BookingStatus.CANCELLED)
                .createdAt(LocalDateTime.now())
                .build());

        // Request free slots with D = 30
        MvcResult result = mockMvc.perform(get("/api/free-slots")
                        .param("date", targetDate.toString())
                        .param("duration", "30")
                        .param("attendees", attendeeA, attendeeB))
                .andExpect(status().isOk())
                .andReturn();

        List<FreeSlotResponse> slots = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<List<FreeSlotResponse>>() {}
        );

        // Expected slots: exactly 09:00–09:30, 11:00–13:00, 14:00–18:00
        assertThat(slots).hasSize(3);

        assertThat(slots.get(0).getStartTime()).isEqualTo(targetDate.atTime(9, 0));
        assertThat(slots.get(0).getEndTime()).isEqualTo(targetDate.atTime(9, 30));

        assertThat(slots.get(1).getStartTime()).isEqualTo(targetDate.atTime(11, 0));
        assertThat(slots.get(1).getEndTime()).isEqualTo(targetDate.atTime(13, 0));

        assertThat(slots.get(2).getStartTime()).isEqualTo(targetDate.atTime(14, 0));
        assertThat(slots.get(2).getEndTime()).isEqualTo(targetDate.atTime(18, 0));

        // For each returned slot, list the active rooms that are free for the whole slot and big enough for the group
        for (FreeSlotResponse slot : slots) {
            assertThat(slot.getAvailableRooms()).isNotEmpty();
            for (var room : slot.getAvailableRooms()) {
                assertThat(room.getCapacity()).isGreaterThanOrEqualTo(2);
                assertThat(room.isActive()).isTrue();
            }
        }
    }

    @Autowired
    private com.edstem.bookingSlot.service.BookingService bookingService;

    @Test
    @DisplayName("Acceptance Criteria: 20 simultaneous requests to book same room at same time -> exactly 1 succeeds, 19 get 409")
    void testConcurrentBookingsUnderHeavyLoad() throws Exception {
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        AtomicInteger otherCount = new AtomicInteger(0);

        LocalDateTime startTime = targetDate.atTime(15, 0);
        LocalDateTime endTime = targetDate.atTime(16, 0);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    CreateBookingRequest request = CreateBookingRequest.builder()
                            .roomId(roomA.getId())
                            .title("Concurrent User Meeting " + index)
                            .organizerEmail("user" + index + "@example.com")
                            .startTime(startTime)
                            .endTime(endTime)
                            .build();

                    bookingService.createBooking(request);
                    successCount.incrementAndGet();
                } catch (com.edstem.bookingSlot.exception.BookingOverlapException e) {
                    // Equivalent to HTTP 409 CONFLICT
                    conflictCount.incrementAndGet();
                } catch (Exception e) {
                    otherCount.incrementAndGet();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        // Fire all threads simultaneously
        startLatch.countDown();
        boolean completed = finishLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(19);
        assertThat(otherCount.get()).isEqualTo(0);

        long countInDb = bookingRepository.findOverlappingActiveBookings(roomA.getId(), startTime, endTime).size();
        assertThat(countInDb).isEqualTo(1);
    }

    @Test
    @DisplayName("Acceptance Criteria: Safe retries - retrying the same booking request creates only one booking")
    void testSafeRetriesIdempotency() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        LocalDateTime startTime = targetDate.atTime(16, 30);
        LocalDateTime endTime = targetDate.atTime(17, 30);

        CreateBookingRequest request = CreateBookingRequest.builder()
                .roomId(roomB.getId())
                .title("Idempotent Client Meeting")
                .organizerEmail("client@example.com")
                .startTime(startTime)
                .endTime(endTime)
                .idempotencyKey(idempotencyKey)
                .build();

        // Initial request
        MvcResult firstResult = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        BookingResponse firstBooking = objectMapper.readValue(firstResult.getResponse().getContentAsString(), BookingResponse.class);
        assertThat(firstBooking.getId()).isNotNull();

        // Retry with same payload and header / key
        MvcResult retryResult = mockMvc.perform(post("/api/bookings")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        BookingResponse retryBooking = objectMapper.readValue(retryResult.getResponse().getContentAsString(), BookingResponse.class);
        assertThat(retryBooking.getId()).isEqualTo(firstBooking.getId());

        // Check DB only has 1 booking
        long totalInDb = bookingRepository.findAll().stream()
                .filter(b -> idempotencyKey.equals(b.getIdempotencyKey()))
                .count();
        assertThat(totalInDb).isEqualTo(1);
    }
}
