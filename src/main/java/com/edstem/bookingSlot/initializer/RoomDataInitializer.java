package com.edstem.bookingSlot.initializer;

import com.edstem.bookingSlot.model.Room;
import com.edstem.bookingSlot.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RoomDataInitializer implements CommandLineRunner {

    private final RoomRepository roomRepository;

    @Override
    public void run(String... args) {
        if (roomRepository.count() == 0) {
            List<Room> initialRooms = List.of(
                    Room.builder().name("Focus Room").capacity(2).active(true).build(),
                    Room.builder().name("Team Room Alpha").capacity(6).active(true).build(),
                    Room.builder().name("Team Room Beta").capacity(8).active(true).build(),
                    Room.builder().name("Boardroom").capacity(12).active(true).build(),
                    Room.builder().name("Workshop Hall").capacity(20).active(true).build()
            );
            roomRepository.saveAll(initialRooms);
        }
    }
}
