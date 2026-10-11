package com.hic.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record DashboardAttendanceDTO(
        LocalDate date, OffsetDateTime updatedAt,
        long attended, long absent, long late, long earlyLeave, long permitted, long onLeave,
        List<AreaOccupancy> areas, long unknownArea, long unresolvedPresence) {
    public record AreaOccupancy(Long areaId, String areaName, long employeeCount) {}
}
