package com.hic.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class EmployeePermissionBulkRequest {
    private List<Long> employeeIds;
    private Long permissionTypeId;
    private LocalDate startDate;
    private LocalDate endDate;
    private String reason;
    private String status;
}
