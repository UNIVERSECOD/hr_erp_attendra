package com.hic.controller;

import com.hic.dto.ApiResponse;
import com.hic.dto.ReportDTO;
import com.hic.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('HEAD_OFFICE_HR','OFFICE_HR','DEPARTMENT_HR')")
public class ReportController {
    private final ReportService reportService;

    @GetMapping("/attendance")
    public ResponseEntity<ApiResponse<List<ReportDTO.AttendanceReportDTO>>> getAttendanceReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Long departmentId) {
        return ResponseEntity.ok(ApiResponse.success(reportService.getAttendanceReport(start, end, branchId, departmentId)));
    }

    @GetMapping("/leave")
    public ResponseEntity<ApiResponse<List<ReportDTO.LeaveReportDTO>>> getLeaveReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(required = false) Long branchId) {
        return ResponseEntity.ok(ApiResponse.success(reportService.getLeaveReport(start, end, branchId)));
    }

    @GetMapping("/employee-summary")
    public ResponseEntity<ApiResponse<List<ReportDTO.EmployeeSummaryDTO>>> getEmployeeSummary(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Long departmentId) {
        return ResponseEntity.ok(ApiResponse.success(reportService.getEmployeeSummary(branchId, departmentId)));
    }
}
