package com.hic.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PermissionDTO {
    private Long id;
    private Long tenantId;
    private Long permissionTypeId;
    private String name;
    private String description;
    private String leaveType;
    private String applyType;
    private Long targetId;
    private String startDate;
    private String endDate;
    private String reason;
    private String status;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long approvedBy;
    private LocalDateTime approvalDate;
    private String employeeName;
    private String employeeId;
    private String finNumber;
}
