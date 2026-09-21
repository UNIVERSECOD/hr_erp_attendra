package com.hic.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsDTO {
    private long totalEmployees;
    private long activeEmployees;
    private long onLeaveEmployees;
    private long activeDevices;
    private long totalDevices;
    private long todayAttendance;
    private long pendingLeaves;
}
