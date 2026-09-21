package com.hic.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Data
public class AttendanceReportRowDTO {
    /** Primary key of attendance_logs (stable UI key). */
    private Long attendanceLogId;
    private Long employeePk;
    private String employeeId;
    private String photoUrl;
    private String fullName;
    private String fin;
    private String department;
    private String position;
    private String area;
    private LocalDate date;
    private OffsetDateTime checkInTime;
    private OffsetDateTime checkOutTime;
    /** Worked duration in minutes for the day. */
    private Integer workedMinutes;
    private String verificationMethod;
    private String shiftType;
}
