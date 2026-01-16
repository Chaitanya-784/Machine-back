package com.factory.backend.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.factory.backend.entity.MachineEvent;
import com.factory.backend.repository.MachineEventRepository;

@Service
public class EventService {

    private final MachineEventRepository repository;

    public EventService(MachineEventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Map<String, Object> processBatch(List<MachineEvent> incomingEvents) {
        int accepted = 0, deduped = 0, updated = 0, rejected = 0;
        
        // 1. Bulk Fetch
        List<String> eventIds = incomingEvents.stream().map(MachineEvent::getEventId).toList();
        Map<String, MachineEvent> existingMap = repository.findAllById(eventIds)
                .stream().collect(Collectors.toMap(MachineEvent::getEventId, Function.identity()));

        List<MachineEvent> toSave = new ArrayList<>();

        for (MachineEvent incoming : incomingEvents) {
            // Validate
            if (!isValid(incoming)) {
                rejected++;
                continue;
            }

            if (existingMap.containsKey(incoming.getEventId())) {
                MachineEvent existing = existingMap.get(incoming.getEventId());
                if (existing.hasSamePayload(incoming)) {
                    deduped++;
                } else if (incoming.getReceivedTime().isAfter(existing.getReceivedTime())) {
                    // Update
                    existing.setMachineId(incoming.getMachineId());
                    existing.setDurationMs(incoming.getDurationMs());
                    existing.setDefectCount(incoming.getDefectCount());
                    existing.setEventTime(incoming.getEventTime());
                    existing.setReceivedTime(incoming.getReceivedTime());
                    toSave.add(existing);
                    updated++;
                } else {
                    deduped++; // Old update ignored
                }
            } else {
                toSave.add(incoming);
                accepted++;
                existingMap.put(incoming.getEventId(), incoming);
            }
        }

        repository.saveAll(toSave);

        return Map.of("accepted", accepted, "deduped", deduped, "updated", updated, "rejected", rejected);
    }

    private boolean isValid(MachineEvent e) {
        if (e.getDurationMs() < 0 || e.getDurationMs() > 21600000) return false; // 6hrs
        if (e.getEventTime().isAfter(Instant.now().plus(15, ChronoUnit.MINUTES))) return false;
        return true;
    }
}