package com.factory.backend.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "machine_events", indexes = {
    @Index(name = "idx_event_id", columnList = "eventId", unique = true),
    @Index(name = "idx_machine_time", columnList = "machineId, eventTime") 
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MachineEvent {
    @Id
    @Column(nullable = false, unique = true)
    private String eventId;

    @Column(nullable = false)
    private String machineId;

    @Column(nullable = false)
    private Instant eventTime;     // For querying
    @Column(nullable = false)
    private Instant receivedTime;  // For logic updates

    private long durationMs;
    private int defectCount;

    // Helper for logic
    public boolean hasSamePayload(MachineEvent other) {
        if (other == null) return false;
        return this.machineId.equals(other.machineId) &&
               this.durationMs == other.durationMs &&
               this.defectCount == other.defectCount &&
               this.eventTime.equals(other.eventTime);
    }
}