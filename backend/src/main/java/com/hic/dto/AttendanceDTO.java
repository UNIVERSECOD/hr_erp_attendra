package com.hic.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AttendanceDTO {
    private Long employeeId;
    private LocalDateTime checkInTime;
    private LocalDateTime checkOutTime;
    private String deviceId;
    private String doorId;
    private String eventType;
    private String verificationMethod;
}
