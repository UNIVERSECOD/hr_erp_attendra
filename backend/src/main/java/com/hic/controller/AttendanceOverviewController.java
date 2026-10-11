package com.hic.controller;

import com.hic.dto.*;
import com.hic.service.AttendanceOverviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/attendance/reports")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('HEAD_OFFICE_HR','OFFICE_HR','DEPARTMENT_HR')")
public class AttendanceOverviewController {
    private final AttendanceOverviewService service;

    @ExceptionHandler(org.springframework.validation.BindException.class)
    public ResponseEntity<ApiResponse<Void>> invalidFilter() {
        return ResponseEntity.badRequest().body(ApiResponse.error("Hesabat filtrinin formatı yanlışdır."));
    }

    @GetMapping
    public ApiResponse<PaginatedResponse<AttendanceOverviewRowDTO>> report(@ModelAttribute AttendanceReportQuery query) {
        return ApiResponse.success(service.report(query));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@ModelAttribute AttendanceReportQuery query) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=attendance.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(service.export(query));
    }
}
