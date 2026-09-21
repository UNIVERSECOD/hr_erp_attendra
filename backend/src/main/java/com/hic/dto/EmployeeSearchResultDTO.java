package com.hic.dto;

import lombok.Data;

@Data
public class EmployeeSearchResultDTO {
    private Long employeePk;
    private String employeeId;
    private String firstName;
    private String lastName;
    private String finNumber;
    private Long departmentId;
    private String departmentName;
    private Long branchId;
    private String shiftType;
}
