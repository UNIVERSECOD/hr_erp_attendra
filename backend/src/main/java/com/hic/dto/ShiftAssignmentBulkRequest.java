package com.hic.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class ShiftAssignmentBulkRequest {
    private List<Long> employeeIds;
    private Long timetableId;
    private LocalDate startDate;
    private LocalDate endDate;
}
