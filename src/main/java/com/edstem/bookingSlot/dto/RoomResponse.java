package com.edstem.bookingSlot.dto;

import com.edstem.bookingSlot.model.Room;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomResponse {

    private Long id;
    private String name;
    private int capacity;
    private boolean active;

    public static RoomResponse fromEntity(Room room) {
        return RoomResponse.builder()
                .id(room.getId())
                .name(room.getName())
                .capacity(room.getCapacity())
                .active(room.isActive())
                .build();
    }
}
