package com.hic.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DepartmentDTO {

    private Long id;

    @NotBlank(message = "Department name is required")
    private String departmentName;

    private String description;

    private Long branchId;

    private Long parentDepartmentId;

    private String parentDepartmentName;

    private long employeeCount;

    private LocalDateTime createdAt;

    private Boolean calculateOvertime;

    private Boolean flexShift;

    private String timetable;

    private Long timetableId;
}
