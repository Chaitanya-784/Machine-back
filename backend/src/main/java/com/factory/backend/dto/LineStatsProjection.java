package com.factory.backend.dto;

public interface LineStatsProjection {
    String getLineId();
    long getEventCount();
    long getTotalDefects();
}
