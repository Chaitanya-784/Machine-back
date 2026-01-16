package com.factory.backend.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.factory.backend.dto.LineStatsProjection;
import com.factory.backend.dto.MachineStats;
import com.factory.backend.dto.TopDefectLine;
import com.factory.backend.entity.MachineEvent;

public interface MachineEventRepository extends JpaRepository<MachineEvent, String> {

    @Query("SELECT new com.factory.backend.dto.MachineStats(" +
           "COUNT(e), " +
           "SUM(CASE WHEN e.defectCount = -1 THEN 0 ELSE e.defectCount END)) " +
           "FROM MachineEvent e " +
           "WHERE e.machineId = :machineId " +
           "AND e.eventTime >= :start AND e.eventTime < :end")
    MachineStats getStats(@Param("machineId") String machineId, 
                          @Param("start") Instant start, 
                          @Param("end") Instant end);

    @Query("SELECT e.machineId as lineId, " +
           "COUNT(e) as eventCount, " +
           "SUM(CASE WHEN e.defectCount = -1 THEN 0 ELSE e.defectCount END) as totalDefects " +
           "FROM MachineEvent e " +
           "WHERE e.eventTime >= :start AND e.eventTime < :end " +
           "GROUP BY e.machineId " +
           "ORDER BY SUM(CASE WHEN e.defectCount = -1 THEN 0 ELSE e.defectCount END) DESC")
    List<TopDefectLine> findTopDefectLines(@Param("start") Instant start, 
                                           @Param("end") Instant end);
       
       
       // --- ADD THIS NEW METHOD ---
    @Query("SELECT e.machineId as lineId, " +
           "COUNT(e) as eventCount, " +
           "COALESCE(SUM(CASE WHEN e.defectCount = -1 THEN 0 ELSE e.defectCount END), 0L) as totalDefects " +
           "FROM MachineEvent e " +
           "WHERE e.eventTime >= :from AND e.eventTime < :to " +
           "GROUP BY e.machineId " +
           "ORDER BY totalDefects DESC")
    List<LineStatsProjection> fetchWorstMachines(@Param("from") Instant from, 
                                                 @Param("to") Instant to);
}


       
