package com.hic.dto;

import lombok.Data;

@Data
public class EmployeeAttendanceSummaryDTO {
    private long totalDays;
    private long workingDays;
    private double totalHours;
    private long absentDays;
    private long lateDays;
    private long leaveDays;
}
