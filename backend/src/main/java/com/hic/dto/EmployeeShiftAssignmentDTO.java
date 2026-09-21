package com.hic.dto;

import com.hic.model.EmployeeShiftAssignment;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class EmployeeShiftAssignmentDTO {
    private Long id;
    private Long tenantId;
    private Long employeeId;
    private Long timetableId;
    private LocalDate effectiveStartDate;
    private LocalDate effectiveEndDate;
    private Long assignedBy;
    private LocalDateTime assignedAt;
    private EmployeeShiftAssignment.Status status;
}
