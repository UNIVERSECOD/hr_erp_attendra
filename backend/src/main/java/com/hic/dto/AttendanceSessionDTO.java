package com.hic.dto;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class AttendanceSessionDTO {
    private OffsetDateTime checkInTime;
    private OffsetDateTime checkOutTime;
}
