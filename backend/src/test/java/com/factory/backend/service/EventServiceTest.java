package com.factory.backend.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List; // IMPORTANT: Needed for Test 6 & 7
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.factory.backend.dto.MachineStats;
import com.factory.backend.entity.MachineEvent;
import com.factory.backend.repository.MachineEventRepository;

@SpringBootTest
@ActiveProfiles("test") 
class EventServiceTest {

    @Autowired
    private EventService eventService;

    @Autowired
    private MachineEventRepository repository;

    @BeforeEach
    void setUp() {
        // Clear database before each test to ensure isolation
        repository.deleteAll();
    }

    // --- 1. Deduplication Tests ---
    
    @Test
    void test1_IdenticalPayloadIsDeduped() {
        // Scenario: Same ID, Same Payload [cite: 43]
        MachineEvent event1 = createEvent("E-1", "M-1", 1000, 0);
        MachineEvent event2 = createEvent("E-1", "M-1", 1000, 0);

        Map<String, Object> result = eventService.processBatch(List.of(event1, event2));

        // Expect: 1 Accepted (first one), 1 Deduped (second one)
        assertEquals(1, (int) result.get("accepted"));
        assertEquals(1, (int) result.get("deduped"));
        assertEquals(1, repository.count()); 
    }

    @Test
    void test2_NewerReceivedTimeUpdates() {
        // Scenario: Same ID, Different Payload, Newer Time -> Update [cite: 44]
        MachineEvent oldEvent = createEvent("E-UPDATE", "M-1", 1000, 0);
        oldEvent.setReceivedTime(Instant.now().minusSeconds(100)); 

        MachineEvent newEvent = createEvent("E-UPDATE", "M-1", 9999, 5);
        newEvent.setReceivedTime(Instant.now()); 

        eventService.processBatch(List.of(oldEvent, newEvent));

        MachineEvent saved = repository.findById("E-UPDATE").orElseThrow();
        assertEquals(9999, saved.getDurationMs()); // Should match newEvent
    }

    @Test
    void test3_OlderReceivedTimeIgnored() {
        // Scenario: Same ID, Different Payload, Older Time -> Ignore [cite: 106]
        MachineEvent existing = createEvent("E-IGNORE", "M-1", 5000, 0);
        existing.setReceivedTime(Instant.now());
        eventService.processBatch(List.of(existing));

        // Now try to insert an "Old" version
        MachineEvent oldVersion = createEvent("E-IGNORE", "M-1", 1000, 0);
        oldVersion.setReceivedTime(Instant.now().minusSeconds(300));

        Map<String, Object> result = eventService.processBatch(List.of(oldVersion));

        assertEquals(1, (int) result.get("deduped"));
        assertEquals(5000, repository.findById("E-IGNORE").get().getDurationMs());
    }

    // --- 2. Validation Tests ---

    @Test
    void test4_InvalidDurationRejected() {
        // Scenario: Duration < 0 or > 6 hours [cite: 46]
        MachineEvent negative = createEvent("E-BAD-1", "M-1", -10, 0);
        MachineEvent tooLong = createEvent("E-BAD-2", "M-1", 22000000, 0); 

        Map<String, Object> result = eventService.processBatch(List.of(negative, tooLong));

        assertEquals(2, (int) result.get("rejected"));
        assertEquals(0, repository.count());
    }

    @Test
    void test5_FutureEventTimeRejected() {
        // Scenario: Event time > 15 mins in future [cite: 46]
        MachineEvent futureEvent = createEvent("E-FUT", "M-1", 1000, 0);
        futureEvent.setEventTime(Instant.now().plus(20, ChronoUnit.MINUTES));

        Map<String, Object> result = eventService.processBatch(List.of(futureEvent));

        assertEquals(1, (int) result.get("rejected"));
    }

    // --- 3. Stats Logic Tests (The Missing Ones) ---

    @Test
    void test6_UnknownDefectIgnoredInStats() {
        // Scenario: DefectCount = -1 ignored in defect totals [cite: 47, 109]
        MachineEvent unknownDefect = createEvent("E-UNK", "M-STATS", 1000, -1);
        eventService.processBatch(List.of(unknownDefect));

        // We assume 1 hour window around 'now' covers the event
        MachineStats stats = repository.getStats("M-STATS", 
            Instant.now().minusSeconds(3600), 
            Instant.now().plusSeconds(3600));

        assertEquals(1, stats.eventsCount()); // It IS a valid event
        assertEquals(0, stats.defectsCount()); // But defects should count as 0, not -1
    }

    @Test
    void test7_TimeWindowBoundaries() {
        // Scenario: start inclusive, end exclusive [cite: 74, 110]
        Instant baseTime = Instant.now();
        
        // Event 1: Exactly at Start Time
        MachineEvent e1 = createEvent("E-TIME-1", "M-BOUND", 1000, 0);
        e1.setEventTime(baseTime);
        
        // Event 2: Exactly at End Time
        MachineEvent e2 = createEvent("E-TIME-2", "M-BOUND", 1000, 0);
        e2.setEventTime(baseTime.plus(1, ChronoUnit.HOURS));

        eventService.processBatch(List.of(e1, e2));

        // Query: Start to Start+1h
        // Should catch E1 (Inclusive Start) but MISS E2 (Exclusive End)
        MachineStats stats = repository.getStats("M-BOUND", 
            baseTime, 
            baseTime.plus(1, ChronoUnit.HOURS));

        assertEquals(1, stats.eventsCount());
    }

    // --- 4. Thread Safety Test ---

    @Test
    void test8_ThreadSafety() throws InterruptedException {
        // Scenario: Concurrent ingestion doesn’t corrupt counts [cite: 111]
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    MachineEvent e = createEvent("E-CON-" + index, "M-1", 100, 0);
                    eventService.processBatch(List.of(e));
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        assertEquals(20, repository.count());
    }

    // --- Helper ---
    private MachineEvent createEvent(String id, String machineId, long duration, int defects) {
        return MachineEvent.builder()
                .eventId(id)
                .machineId(machineId)
                .eventTime(Instant.now())
                .receivedTime(Instant.now())
                .durationMs(duration)
                .defectCount(defects)
                .build();
    }
}