package com.factory.backend.benchmark;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.factory.backend.entity.MachineEvent;
import com.factory.backend.repository.MachineEventRepository;
import com.factory.backend.service.EventService;

@SpringBootTest
@ActiveProfiles("test")
public class IngestionBenchmarkTest {

    @Autowired
    private EventService eventService;

    @Autowired
    private MachineEventRepository repository;

    @Test
    public void runBenchmark() {
        // 1. Clear DB to ensure a clean slate (optional, but good for consistency)
        repository.deleteAll();

        // 2. Generate 1,000 dummy events
        int batchSize = 1000;
        System.out.println("Generating " + batchSize + " events...");
        List<MachineEvent> batch = generateEvents(batchSize);

        // 3. Measure Execution Time
        long startTime = System.currentTimeMillis();
        
        eventService.processBatch(batch);
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // 4. Print Results to Console
        System.out.println("\n\n========================================");
        System.out.println("BENCHMARK RESULT");
        System.out.println("Events Processed: " + batchSize);
        System.out.println("Time Taken:       " + duration + " ms");
        System.out.println("Events/Second:    " + (batchSize / (duration / 1000.0)));
        System.out.println("========================================\n\n");
    }

    // --- Helper: Data Generator ---
    private List<MachineEvent> generateEvents(int count) {
        List<MachineEvent> events = new ArrayList<>();
        Random random = new Random();
        Instant now = Instant.now();

        for (int i = 0; i < count; i++) {
            events.add(MachineEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .machineId("M-" + random.nextInt(10)) // 10 Random machines
                .eventTime(now.minusSeconds(random.nextInt(3600)))
                .receivedTime(now)
                .durationMs(100 + random.nextInt(5000))
                .defectCount(random.nextInt(10) == 0 ? -1 : random.nextInt(5)) // 10% chance of -1
                .build()
            );
        }
        return events;
    }
}