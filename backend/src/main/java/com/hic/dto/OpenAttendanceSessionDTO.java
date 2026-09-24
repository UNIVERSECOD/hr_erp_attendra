package com.hic.dto;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class OpenAttendanceSessionDTO {
    private Long attendanceLogId;
    private Long employeePk;
    private String employeeId;
    private String fullName;
    private OffsetDateTime checkInTime;
    private String shiftType;
    private String status;
    private Boolean manualOverride;
}
