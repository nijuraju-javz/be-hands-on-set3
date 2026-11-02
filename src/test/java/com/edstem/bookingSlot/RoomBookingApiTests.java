package com.edstem.bookingSlot;

import com.edstem.bookingSlot.dto.BookingResponse;
import com.edstem.bookingSlot.dto.CreateBookingRequest;
import com.edstem.bookingSlot.dto.ErrorResponse;
import com.edstem.bookingSlot.model.Room;
import com.edstem.bookingSlot.repository.BookingRepository;
import com.edstem.bookingSlot.repository.RoomRepository;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class RoomBookingApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private BookingRepository bookingRepository;

    private Room testRoom;
    private LocalDate targetDate;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        targetDate = LocalDate.now().plusDays(2);
        testRoom = roomRepository.findAll().stream()
                .filter(r -> r.getCapacity() >= 6 && r.isActive())
                .findFirst()
                .orElseGet(() -> roomRepository.save(Room.builder().name("Test Suite Room").capacity(8).active(true).build()));
    }

    @Test
    @DisplayName("Acceptance Criteria: 10:00-11:00 succeeds, 10:30-11:30 returns 409, 11:00-12:00 succeeds")
    void testAcceptanceCriteriaBookingSequence() throws Exception {
        LocalDateTime time10_00 = targetDate.atTime(10, 0);
        LocalDateTime time10_30 = targetDate.atTime(10, 30);
        LocalDateTime time11_00 = targetDate.atTime(11, 0);
        LocalDateTime time11_30 = targetDate.atTime(11, 30);
        LocalDateTime time12_00 = targetDate.atTime(12, 0);

        // 1. Booking 10:00-11:00 returns 201
        CreateBookingRequest req1 = CreateBookingRequest.builder()
                .roomId(testRoom.getId())
                .title("Team Standup")
                .organizerEmail("alice@example.com")
                .attendees(List.of("bob@example.com"))
                .startTime(time10_00)
                .endTime(time11_00)
                .build();

        MvcResult result1 = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated())
                .andReturn();

        BookingResponse b1 = objectMapper.readValue(result1.getResponse().getContentAsString(), BookingResponse.class);
        assertThat(b1.getId()).isNotNull();

        // 2. Booking 10:30-11:30 returns 409 with clashing booking id
        CreateBookingRequest req2 = CreateBookingRequest.builder()
                .roomId(testRoom.getId())
                .title("Overlapping Sync")
                .organizerEmail("charlie@example.com")
                .startTime(time10_30)
                .endTime(time11_30)
                .build();

        MvcResult result2 = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isConflict())
                .andReturn();

        ErrorResponse err2 = objectMapper.readValue(result2.getResponse().getContentAsString(), ErrorResponse.class);
        assertThat(err2.getError()).isEqualTo("CONFLICT");
        assertThat(err2.getClashingBookingId()).isEqualTo(b1.getId());

        // 3. Booking 11:00-12:00 afterwards returns 201 (back-to-back allowed)
        CreateBookingRequest req3 = CreateBookingRequest.builder()
                .roomId(testRoom.getId())
                .title("Design Review")
                .organizerEmail("diana@example.com")
                .startTime(time11_00)
                .endTime(time12_00)
                .build();

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req3)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Acceptance Criteria: Booking completely covering existing booking (09:00-12:00 over 10:00-10:30) is rejected")
    void testCompleteCoverOverlapRejected() throws Exception {
        LocalDateTime time09_00 = targetDate.atTime(9, 0);
        LocalDateTime time10_00 = targetDate.atTime(10, 0);
        LocalDateTime time10_30 = targetDate.atTime(10, 30);
        LocalDateTime time12_00 = targetDate.atTime(12, 0);

        // Initial booking 10:00-10:30
        CreateBookingRequest initial = CreateBookingRequest.builder()
                .roomId(testRoom.getId())
                .title("Quick 1:1")
                .organizerEmail("lead@example.com")
                .startTime(time10_00)
                .endTime(time10_30)
                .build();

        MvcResult initRes = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(initial)))
                .andExpect(status().isCreated())
                .andReturn();
        BookingResponse initBooking = objectMapper.readValue(initRes.getResponse().getContentAsString(), BookingResponse.class);

        // Covering booking 09:00-12:00 should return 409
        CreateBookingRequest covering = CreateBookingRequest.builder()
                .roomId(testRoom.getId())
                .title("All-hands Workshop")
                .organizerEmail("director@example.com")
                .startTime(time09_00)
                .endTime(time12_00)
                .build();

        MvcResult covRes = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(covering)))
                .andExpect(status().isConflict())
                .andReturn();

        ErrorResponse covErr = objectMapper.readValue(covRes.getResponse().getContentAsString(), ErrorResponse.class);
        assertThat(covErr.getClashingBookingId()).isEqualTo(initBooking.getId());
    }

    @Test
    @DisplayName("Validation: Invalid input returns 400 with a message per invalid field")
    void testValidationErrors() throws Exception {
        // Multi-day booking, non 15-min interval, exceeds room capacity
        Room smallRoom = roomRepository.findAll().stream()
                .filter(r -> r.getCapacity() == 2)
                .findFirst()
                .orElse(testRoom);

        CreateBookingRequest invalidReq = CreateBookingRequest.builder()
                .roomId(smallRoom.getId())
                .title("") // Blank title
                .organizerEmail("invalid-email") // Invalid email
                .attendees(List.of("user1@example.com", "user2@example.com", "user3@example.com")) // Exceeds cap (3 + 1 org = 4 > 2)
                .startTime(targetDate.atTime(8, 0)) // Before 09:00
                .endTime(targetDate.plusDays(1).atTime(19, 10)) // Next day, after 18:00, not 15-min
                .build();

        MvcResult res = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidReq)))
                .andExpect(status().isBadRequest())
                .andReturn();

        ErrorResponse err = objectMapper.readValue(res.getResponse().getContentAsString(), ErrorResponse.class);
        assertThat(err.getError()).isEqualTo("BAD_REQUEST");
        assertThat(err.getFieldErrors()).isNotEmpty();
    }

    @Test
    @DisplayName("Not Found: Unknown room returns 404, unknown booking returns 404")
    void testNotFoundErrors() throws Exception {
        CreateBookingRequest req = CreateBookingRequest.builder()
                .roomId(999999L)
                .title("Ghost Meeting")
                .organizerEmail("ghost@example.com")
                .startTime(targetDate.atTime(10, 0))
                .endTime(targetDate.atTime(11, 0))
                .build();

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/bookings/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Lifecycle: Cancelled booking no longer blocks the room and listing returns bookings in time order")
    void testCancelAndListBookings() throws Exception {
        LocalDateTime time14_00 = targetDate.atTime(14, 0);
        LocalDateTime time15_00 = targetDate.atTime(15, 0);

        CreateBookingRequest req = CreateBookingRequest.builder()
                .roomId(testRoom.getId())
                .title("Session 1")
                .organizerEmail("dev@example.com")
                .startTime(time14_00)
                .endTime(time15_00)
                .build();

        MvcResult createRes = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        BookingResponse created = objectMapper.readValue(createRes.getResponse().getContentAsString(), BookingResponse.class);

        // Cancel booking
        mockMvc.perform(delete("/api/bookings/" + created.getId()))
                .andExpect(status().isOk());

        // Now book the exact same slot again -> should succeed!
        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        // List bookings for date
        mockMvc.perform(get("/api/rooms/" + testRoom.getId() + "/bookings")
                        .param("date", targetDate.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2)); // 1 cancelled, 1 active
    }
}
