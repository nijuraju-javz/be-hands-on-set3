package com.edstem.bookingSlot.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FreeSlotResponse {

    private LocalDateTime startTime;
    private LocalDateTime endTime;
    @Builder.Default
    private List<RoomResponse> availableRooms = new ArrayList<>();
}
