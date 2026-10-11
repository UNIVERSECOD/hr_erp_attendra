package com.hic.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDate;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class AttendanceOverviewRowDTO extends AttendanceReportRowDTO {
    private String period;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private Long timetableId;
    private String timetableName;
    private Integer overtimeMinutes;
    private int lateMinutes;
    private int earlyLeaveMinutes;
    private int permissionMinutes;
    private int attendedDays;
    private int absentDays;
    private int lateDays;
    private int earlyLeaveDays;
    private int leaveDays;
    private int missingExitDays;
    private List<String> statuses = List.of();
}
