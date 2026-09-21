package com.hic.dto;

import com.hic.model.EmployeePermission;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class EmployeePermissionDTO {
    private Long id;
    private Long tenantId;
    private Long employeeId;
    private Long permissionTypeId;
    private LocalDate startDate;
    private LocalDate endDate;
    private String reason;
    private EmployeePermission.Status status;
    private Long approvedBy;
    private LocalDateTime approvalDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
