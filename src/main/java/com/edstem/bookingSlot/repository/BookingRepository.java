package com.edstem.bookingSlot.repository;

import com.edstem.bookingSlot.model.Booking;
import com.edstem.bookingSlot.model.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    @Query("SELECT b FROM Booking b WHERE b.room.id = :roomId AND b.status = com.edstem.bookingSlot.model.BookingStatus.ACTIVE AND b.startTime < :endTime AND b.endTime > :startTime ORDER BY b.startTime ASC")
    List<Booking> findOverlappingActiveBookings(
            @Param("roomId") Long roomId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    @Query("SELECT b FROM Booking b WHERE b.room.id = :roomId AND b.startTime >= :dayStart AND b.startTime < :dayEnd ORDER BY b.startTime ASC")
    List<Booking> findByRoomIdAndDateRange(
            @Param("roomId") Long roomId,
            @Param("dayStart") LocalDateTime dayStart,
            @Param("dayEnd") LocalDateTime dayEnd
    );

    Optional<Booking> findByIdempotencyKey(String idempotencyKey);

    @Query("SELECT DISTINCT b FROM Booking b LEFT JOIN b.attendees a WHERE b.status = com.edstem.bookingSlot.model.BookingStatus.ACTIVE AND b.startTime >= :dayStart AND b.startTime < :dayEnd AND (b.organizerEmail IN :attendees OR a IN :attendees)")
    List<Booking> findActiveBookingsForAttendeesOnDate(
            @Param("attendees") Collection<String> attendees,
            @Param("dayStart") LocalDateTime dayStart,
            @Param("dayEnd") LocalDateTime dayEnd
    );

    @Query("SELECT b FROM Booking b WHERE b.status = com.edstem.bookingSlot.model.BookingStatus.ACTIVE AND b.startTime >= :dayStart AND b.startTime < :dayEnd")
    List<Booking> findActiveBookingsOnDate(
            @Param("dayStart") LocalDateTime dayStart,
            @Param("dayEnd") LocalDateTime dayEnd
    );
}
