package com.hic.controller;

import com.hic.dto.ApiResponse;
import com.hic.dto.AttendanceLogSyncDTO;
import com.hic.dto.DashboardStatsDTO;
import com.hic.model.DeviceConfig;
import com.hic.repository.AttendanceLogRepository;
import com.hic.repository.DeviceConfigRepository;
import com.hic.repository.EmployeeRepository;
import com.hic.repository.LeaveRequestRepository;
import com.hic.service.AttendanceLogSyncService;
import com.hic.util.AppTimeZone;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.OffsetDateTime;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private DeviceConfigRepository deviceConfigRepository;
    @Mock
    private AttendanceLogRepository attendanceLogRepository;
    @Mock
    private LeaveRequestRepository leaveRequestRepository;
    @Mock
    private AttendanceLogSyncService attendanceLogSyncService;
    @InjectMocks
    private DashboardController dashboardController;

    @Test
    void getDeviceStatus_usesRecentDatabaseSyncTimes() {
        DeviceConfig online = new DeviceConfig();
        online.setStatus("ACTIVE");
        online.setLastSyncTime(LocalDateTime.now().minusMinutes(2));
        DeviceConfig stale = new DeviceConfig();
        stale.setStatus("ACTIVE");
        stale.setLastSyncTime(LocalDateTime.now().minusMinutes(20));
        DeviceConfig inactive = new DeviceConfig();
        inactive.setStatus("INACTIVE");
        inactive.setLastSyncTime(LocalDateTime.now());
        when(deviceConfigRepository.findAll()).thenReturn(List.of(online, stale, inactive));

        ResponseEntity<ApiResponse<Map<String, Object>>> response = dashboardController.getDeviceStatus();

        Map<String, Object> data = response.getBody().getData();
        assertThat(data.get("totalDevices")).isEqualTo(3L);
        assertThat(data.get("onlineDevices")).isEqualTo(1L);
        assertThat(data.get("offlineDevices")).isEqualTo(2L);
    }

    @Test
    void getStats_countsDistinctEmployeesWithEntryToday() {
        LocalDateTime todayStart = AppTimeZone.today().atStartOfDay();
        LocalDateTime tomorrowStart = todayStart.plusDays(1);
        when(attendanceLogRepository.countDistinctEmployeesCheckedIn(todayStart, tomorrowStart))
                .thenReturn(3L);

        ResponseEntity<ApiResponse<DashboardStatsDTO>> response = dashboardController.getStats();

        assertThat(response.getBody().getData().getTodayAttendance()).isEqualTo(3L);
        verify(attendanceLogRepository).countDistinctEmployeesCheckedIn(todayStart, tomorrowStart);
    }

    @Test
    void getLatestAccessLogs_mapsIsapiAttendanceEntries() {
        OffsetDateTime now = OffsetDateTime.now();
        when(attendanceLogSyncService.getAttendanceLogs(null, null, 10)).thenReturn(List.of(
                new AttendanceLogSyncDTO.AttendanceLogEntryDTO(11L, 5L, "1001", now, 88L)
        ));

        ResponseEntity<ApiResponse<List<DashboardController.DashboardAccessLogDTO>>> response =
                dashboardController.getLatestAccessLogs();

        List<DashboardController.DashboardAccessLogDTO> logs = response.getBody().getData();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).id()).isEqualTo(11L);
        assertThat(logs.get(0).employeeNo()).isEqualTo("1001");
        assertThat(logs.get(0).punchTime()).isEqualTo(now);
        assertThat(logs.get(0).deviceId()).isEqualTo(5L);
        assertThat(logs.get(0).rawEventId()).isEqualTo(88L);
        assertThat(logs.get(0).status()).isEqualTo("RECORDED");
    }
}
