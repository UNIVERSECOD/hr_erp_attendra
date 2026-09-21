package com.hic.controller;

import com.hic.dto.ApiResponse;
import com.hic.dto.DashboardStatsDTO;
// DeviceSyncDTO import removed — no longer needed
import com.hic.model.Employee.EmploymentStatus;
import com.hic.model.LeaveRequest.LeaveStatus;
import com.hic.repository.AttendanceLogRepository;
import com.hic.repository.DeviceConfigRepository;
import com.hic.repository.EmployeeRepository;
import com.hic.repository.LeaveRequestRepository;
import com.hic.service.AttendanceLogSyncService;
// DeviceSyncService removed — device-status now uses DB directly
import com.hic.util.AppTimeZone;
import com.hic.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private static final String DEFAULT_LOG_STATUS = "RECORDED";

    private final EmployeeRepository employeeRepository;
    private final DeviceConfigRepository deviceConfigRepository;
    private final AttendanceLogRepository attendanceLogRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final AttendanceLogSyncService attendanceLogSyncService;


    @GetMapping
    public ResponseEntity<ApiResponse<DashboardStatsDTO>> getStats() {
        Long tenantId = TenantContext.getTenantId();

        long totalEmployees;
        long activeEmployees;
        long onLeaveEmployees;
        long activeDevices;
        long totalDevices;
        long todayAttendance;
        long pendingLeaves;

        LocalDateTime todayStart = AppTimeZone.today().atStartOfDay();
        LocalDateTime tomorrowStart = todayStart.plusDays(1);

        if (tenantId != null) {
            totalEmployees = employeeRepository.countByTenantId(tenantId);
            activeEmployees = employeeRepository.countByTenantIdAndEmploymentStatus(tenantId, EmploymentStatus.ACTIVE);
            onLeaveEmployees = employeeRepository.countByTenantIdAndEmploymentStatus(tenantId, EmploymentStatus.ON_LEAVE);
            activeDevices = deviceConfigRepository.countByTenantIdAndStatus(tenantId, "ACTIVE");
            totalDevices = deviceConfigRepository.countByTenantId(tenantId);
            todayAttendance = attendanceLogRepository.countDistinctEmployeesCheckedIn(
                    tenantId, todayStart, tomorrowStart);
            pendingLeaves = leaveRequestRepository.countByTenantIdAndStatus(tenantId, LeaveStatus.PENDING);
        } else {
            totalEmployees = employeeRepository.count();
            activeEmployees = employeeRepository.countByEmploymentStatus(EmploymentStatus.ACTIVE);
            onLeaveEmployees = employeeRepository.countByEmploymentStatus(EmploymentStatus.ON_LEAVE);
            activeDevices = deviceConfigRepository.countByStatus("ACTIVE");
            totalDevices = deviceConfigRepository.count();
            todayAttendance = attendanceLogRepository.countDistinctEmployeesCheckedIn(todayStart, tomorrowStart);
            pendingLeaves = leaveRequestRepository.findByStatus(LeaveStatus.PENDING).size();
        }

        DashboardStatsDTO stats = DashboardStatsDTO.builder()
                .totalEmployees(totalEmployees)
                .activeEmployees(activeEmployees)
                .onLeaveEmployees(onLeaveEmployees)
                .activeDevices(activeDevices)
                .totalDevices(totalDevices)
                .todayAttendance(todayAttendance)
                .pendingLeaves(pendingLeaves)
                .build();

        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSummary() {
        Long tenantId = TenantContext.getTenantId();

        long totalEmployees = tenantId != null
                ? employeeRepository.countByTenantId(tenantId)
                : employeeRepository.count();
        long activeEmployees = tenantId != null
                ? employeeRepository.countByTenantIdAndEmploymentStatus(tenantId, EmploymentStatus.ACTIVE)
                : employeeRepository.countByEmploymentStatus(EmploymentStatus.ACTIVE);
        long onLeaveEmployees = tenantId != null
                ? employeeRepository.countByTenantIdAndEmploymentStatus(tenantId, EmploymentStatus.ON_LEAVE)
                : employeeRepository.countByEmploymentStatus(EmploymentStatus.ON_LEAVE);
        long pendingLeaves = tenantId != null
                ? leaveRequestRepository.countByTenantIdAndStatus(tenantId, LeaveStatus.PENDING)
                : leaveRequestRepository.findByStatus(LeaveStatus.PENDING).size();

        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "totalEmployees", totalEmployees,
                "activeEmployees", activeEmployees,
                "onLeaveEmployees", onLeaveEmployees,
                "pendingLeaves", pendingLeaves
        )));
    }

    @GetMapping("/device-status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDeviceStatus() {
        Long tenantId = TenantContext.getTenantId();
        // Use DB records so this endpoint works even when ISAPI is unreachable.
        // A device is considered "online" if its lastSyncTime is within the last 10 minutes.
        java.time.LocalDateTime onlineThreshold = java.time.LocalDateTime.now().minusMinutes(10);

        java.util.List<com.hic.model.DeviceConfig> devices = tenantId != null
                ? deviceConfigRepository.findByTenantId(tenantId)
                : deviceConfigRepository.findAll();

        long totalDevices = devices.size();
        long onlineDevices = devices.stream()
                .filter(d -> !"INACTIVE".equalsIgnoreCase(d.getStatus()) && d.getLastSyncTime() != null && d.getLastSyncTime().isAfter(onlineThreshold))
                .count();
        long offlineDevices = totalDevices - onlineDevices;

        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "totalDevices", totalDevices,
                "onlineDevices", onlineDevices,
                "offlineDevices", offlineDevices
        )));
    }

    @GetMapping("/access-logs/latest")
    public ResponseEntity<ApiResponse<List<DashboardAccessLogDTO>>> getLatestAccessLogs() {
        List<DashboardAccessLogDTO> latest = attendanceLogSyncService.getAttendanceLogs(null, null, 10).stream()
                .map(log -> new DashboardAccessLogDTO(
                        log.getId(),
                        log.getEmployeeNo(),
                        log.getPunchTime(),
                        log.getDeviceId(),
                        log.getRawEventId(),
                        DEFAULT_LOG_STATUS,
                        log.getFirstName(),
                        log.getLastName(),
                        log.getDeviceName(),
                        log.getDoorRole()
                ))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(latest));
    }

    @GetMapping("/current-time")
    public ResponseEntity<ApiResponse<Map<String, String>>> getCurrentTime() {
        LocalDateTime now = LocalDateTime.now();
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "datetime", now.toString(),
                "formatted", now.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"))
        )));
    }

    public record DashboardAccessLogDTO(
            Long id,
            String employeeNo,
            java.time.OffsetDateTime punchTime,
            Long deviceId,
            Long rawEventId,
            String status,
            String firstName,
            String lastName,
            String deviceName,
            String doorRole
    ) {
    }
}
