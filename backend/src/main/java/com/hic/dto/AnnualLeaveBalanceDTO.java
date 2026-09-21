package com.hic.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AnnualLeaveBalanceDTO {
    private Long id;
    private Long employeeId;
    private String employeeName;
    private Integer year;
    private Integer entitlementDays;
    private Integer usedDays;
    private Integer remainingDays;
    private Integer carryoverDays;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
