package com.hic.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ReportDTO {
    private String reportType;
    private LocalDate startDate;
    private LocalDate endDate;
    private Long branchId;
    private Long departmentId;

    @Data
    public static class AttendanceReportDTO {
        private Long employeeId;
        private String employeeName;
        private Integer presentDays;
        private Integer absentDays;
        private Integer lateDays;
        private Double totalHoursWorked;
    }

    @Data
    public static class LeaveReportDTO {
        private Long leaveRequestId;
        private Long employeeId;
        private String employeeName;
        private String leaveTypeName;
        private LocalDate startDate;
        private LocalDate endDate;
        private String status;
    }

    @Data
    public static class EmployeeSummaryDTO {
        private Long employeeId;
        private String employeeName;
        private String departmentName;
        private String employmentStatus;
    }
}
