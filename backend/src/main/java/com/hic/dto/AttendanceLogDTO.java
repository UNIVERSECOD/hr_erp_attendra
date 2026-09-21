package com.hic.dto;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class AttendanceLogDTO {
    private Long id;
    private Long employeeId;
    private String employeeName;
    private OffsetDateTime checkInTime;
    private OffsetDateTime checkOutTime;
    private String deviceId;
    private String doorId;
    private String eventType;
    private String verificationMethod;
    private String status;
    private OffsetDateTime createdAt;
}
