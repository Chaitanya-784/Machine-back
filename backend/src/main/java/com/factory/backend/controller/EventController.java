package com.factory.backend.controller;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.factory.backend.dto.LineStatsProjection;
import com.factory.backend.dto.MachineStats;
import com.factory.backend.entity.MachineEvent;
import com.factory.backend.repository.MachineEventRepository;
import com.factory.backend.service.EventService;

@RestController
public class EventController {

    private final EventService service;
    private final MachineEventRepository repository;

    public EventController(EventService service, MachineEventRepository repository) {
        this.service = service;
        this.repository = repository;
    }

    @PostMapping("/events/batch")
    public Map<String, Object> ingestBatch(@RequestBody List<MachineEvent> events) {
        // In a real app, you would map a DTO to the Entity here. 
        // For this assignment, we use the Entity directly for speed.
        return service.processBatch(events);
    }

    @GetMapping("/stats")
    public Map<String, Object> getStats(@RequestParam String machineId, 
                                        @RequestParam Instant start, 
                                        @RequestParam Instant end) {
        MachineStats stats = repository.getStats(machineId, start, end);
        
        // Calculate Logic
        long count = stats.eventsCount() == 0 ? 0 : stats.eventsCount(); // Avoid /0
        double defectRate = 0.0;
        
        // "windowHours" calculation logic from PDF
        // Note: Simplification -> Assuming rate based on event counts or time?
        // PDF says: defectsCount / windowHours.
        long seconds = java.time.Duration.between(start, end).getSeconds();
        double windowHours =  seconds/ 3600.0;
        if (windowHours > 0) {
            defectRate = stats.defectsCount() / windowHours;
        }

        String status = defectRate < 2.0 ? "Healthy" : "Warning";

        return Map.of(
            "machineId", machineId,
            "start", start.toString(), // Return as String (ISO format) matches example [cite: 87]
            "end", end.toString(),
            "eventsCount", count,
            "defectsCount", stats.defectsCount(),
            "avgDefectRate", defectRate,
            "status", status
        );
    }
    @GetMapping("/stats/top-defect-lines")
    public List<Map<String, Object>> getTopWorstMachine(
            @RequestParam String machineId, 
            @RequestParam Instant from, 
            @RequestParam Instant to, 
            @RequestParam int limit) {

        // 1. Fetch raw data from Repository
        // (This uses the 'fetchWorstMachines' method we added to the Repo earlier)
        List<LineStatsProjection> rawList = repository.fetchWorstMachines(from, to);

        // 2. Process logic and build response list
        return rawList.stream()
            .map(line -> {
                long defects = line.getTotalDefects();
                long events = line.getEventCount();
                
                // Logic: "Defect per 100 Events, rounded to 2 decimals" [cite: 101]
                double percent = 0.0;
                if (events > 0) {
                    double raw = ((double) defects / events) * 100.0;
                    // Rounding logic similar to your defectRate style
                    percent = Math.round(raw * 100.0) / 100.0; 
                }

                // 3. Construct Map (Just like your getStats return style)
                return Map.<String, Object>of(
                    "lineId", line.getLineId(),
                    "totalDefects", defects,
                    "eventCount", events,
                    "defectsPercent", percent
                );
            })
            // 4. Apply Limit
            .limit(limit) 
            .collect(Collectors.toList());
    }

}