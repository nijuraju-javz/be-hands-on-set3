package com.edstem.bookingSlot.service;

import com.edstem.bookingSlot.dto.RoomResponse;
import com.edstem.bookingSlot.exception.ResourceNotFoundException;
import com.edstem.bookingSlot.model.Room;
import com.edstem.bookingSlot.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;

    @Transactional(readOnly = true)
    public List<RoomResponse> getAllRooms() {
        return roomRepository.findAll().stream()
                .map(RoomResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public Room getRoomById(Long id) {
        return roomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found with ID: " + id));
    }

    @Transactional(readOnly = true)
    public RoomResponse getRoomResponseById(Long id) {
        return RoomResponse.fromEntity(getRoomById(id));
    }
}
