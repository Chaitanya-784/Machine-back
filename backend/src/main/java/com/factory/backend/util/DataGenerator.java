package com.factory.backend.util;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import com.factory.backend.entity.MachineEvent;

public class DataGenerator {

    private static final Random random = new Random();

    // Generates a batch of unique events (for Benchmark)
    public static List<MachineEvent> generateBatch(int count) {
        List<MachineEvent> events = new ArrayList<>();
        Instant now = Instant.now();

        for (int i = 0; i < count; i++) {
            MachineEvent event = new MachineEvent();
            
            // 1. Unique Event ID
            event.setEventId(UUID.randomUUID().toString());
            
            // 2. Machine ID (Simulate 10 different machines)
            event.setMachineId("M-00" + (random.nextInt(10) + 1));
            
            // 3. Time (Spread out slightly to look real)
            event.setEventTime(now.minusSeconds(random.nextInt(3600))); 
            event.setReceivedTime(now);

            // 4. Duration (Valid range: 1ms to 6 hours)
            // 6 hours = 21,600,000 ms
            long duration = 100 + random.nextInt(20000000); 
            event.setDurationMs(duration);

            // 5. Defect Count (Include -1 logic)
            // 10% chance of being -1 (Unknown), otherwise 0-5 defects
            if (random.nextInt(10) == 0) {
                event.setDefectCount(-1); // Special rule test
            } else {
                event.setDefectCount(random.nextInt(5));
            }

            events.add(event);
        }
        return events;
    }
}