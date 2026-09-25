package com.hic.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Data
public class EmployeePermissionBulkRequest {
    private List<Long> employeeIds;
    private Long permissionTypeId;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private Boolean deductFromWorkHours;
    private String reason;
    private String status;
}
