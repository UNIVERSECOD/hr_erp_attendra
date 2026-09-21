package com.hic.controller;

import com.hic.dto.ApiResponse;
import com.hic.dto.AttendanceLogSyncDTO;
import com.hic.service.AttendanceLogSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('HEAD_OFFICE_HR','OFFICE_HR','DEPARTMENT_HR')")
public class LogsController {

    private final AttendanceLogSyncService attendanceLogSyncService;

    @GetMapping("/attendance")
    public ResponseEntity<ApiResponse<List<AttendanceLogSyncDTO.AttendanceLogEntryDTO>>> getAttendanceLogs(
            @RequestParam(required = false) Long deviceId,
            @RequestParam(required = false) String employeeNo,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) OffsetDateTime start,
            @RequestParam(required = false) OffsetDateTime end,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        if (start != null || end != null || page != null || size != null) {
            return ResponseEntity.ok(ApiResponse.success(
                    attendanceLogSyncService.getAttendanceLogs(deviceId, employeeNo, start, end, page, size)
            ));
        }
        return ResponseEntity.ok(ApiResponse.success(
                attendanceLogSyncService.getAttendanceLogs(deviceId, employeeNo, limit)
        ));
    }
}
