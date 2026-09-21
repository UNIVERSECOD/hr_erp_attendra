package com.hic.service;

import com.hic.dto.AttendanceLogSyncDTO;
import com.hic.dto.DoorAttendanceSyncResultDTO;
import com.hic.model.AttendanceLog;
import com.hic.model.DeviceConfig;
import com.hic.model.Employee;
import com.hic.repository.AttendanceLogRepository;
import com.hic.repository.DeviceConfigRepository;
import com.hic.repository.EmployeeRepository;
import com.hic.util.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DoorAttendanceSyncServiceTest {

    @Mock private AttendanceLogSyncService attendanceLogSyncService;
    @Mock private AttendanceLogRepository attendanceLogRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private DeviceConfigRepository deviceConfigRepository;
    @Mock private AttendanceCalculationService attendanceCalculationService;
    @Mock private AttendanceService attendanceService;
    @Mock private EmployeeShiftResolver employeeShiftResolver;

    @InjectMocks
    private DoorAttendanceSyncService doorAttendanceSyncService;

    private final AtomicLong logIdSeq = new AtomicLong(1);

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
        logIdSeq.set(1);
        lenient().when(employeeShiftResolver.resolve(any(), any()))
                .thenReturn(new EmployeeShiftResolver.ResolvedShift(null, "FLEXIBLE", true));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void syncAllDevices_noActiveEntryExitDevices_returnsEmptyResult() {
        when(deviceConfigRepository.findByTenantId(1L)).thenReturn(List.of());

        DoorAttendanceSyncResultDTO result = doorAttendanceSyncService.syncAllDevices(
                LocalDateTime.of(2026, 7, 1, 0, 0),
                LocalDateTime.of(2026, 7, 2, 0, 0),
                100);

        assertThat(result.getTotalPunches()).isZero();
        assertThat(result.getMatchedSessions()).isZero();
        assertThat(result.getCreatedLogs()).isZero();
        assertThat(result.getSkippedEmployees()).isZero();
        assertThat(result.getUnresolvedEmployeeNos()).isEmpty();
    }

    @Test
    void syncAllDevices_duplicateEntry_movesOpenCheckInForward() {
        DeviceConfig entryDevice = device(10L, "101", "ENTRY");
        DeviceConfig exitDevice = device(11L, "102", "EXIT");
        Employee employee = employee(5L, "1001");

        stubDevicesAndEmployee(entryDevice, exitDevice, employee);
        when(attendanceLogRepository.findFirstByTenantIdAndEmployeeIdAndCheckOutTimeIsNullOrderByCheckInTimeDesc(1L, 5L))
                .thenReturn(Optional.empty());
        when(attendanceLogRepository.findByTenantIdAndEmployeeIdAndCheckInTime(eq(1L), eq(5L), any()))
                .thenReturn(Optional.empty());
        when(attendanceLogRepository.save(any(AttendanceLog.class))).thenAnswer(invocation -> {
            AttendanceLog log = invocation.getArgument(0);
            if (log.getId() == null) {
                log.setId(logIdSeq.getAndIncrement());
            }
            return log;
        });

        LocalDate day = LocalDate.of(2026, 7, 1);
        when(attendanceLogSyncService.getAttendanceLogs(eq(101L), isNull(), any(), any(), eq(0), anyInt()))
                .thenReturn(List.of(
                        punch("1001", 101L, day.atTime(9, 0)),
                        punch("1001", 101L, day.atTime(9, 20))
                ));
        when(attendanceLogSyncService.getAttendanceLogs(eq(102L), isNull(), any(), any(), eq(0), anyInt()))
                .thenReturn(List.of());

        doorAttendanceSyncService.syncAllDevices(day.atStartOfDay(), day.plusDays(1).atStartOfDay(), 100);

        ArgumentCaptor<AttendanceLog> saved = ArgumentCaptor.forClass(AttendanceLog.class);
        verify(attendanceLogRepository, atLeastOnce()).save(saved.capture());
        List<AttendanceLog> logs = saved.getAllValues();

        assertThat(logs).hasSizeGreaterThanOrEqualTo(2);
        AttendanceLog finalOpen = logs.get(logs.size() - 1);
        assertThat(finalOpen.getCheckInTime()).isEqualTo(day.atTime(9, 20));
        assertThat(finalOpen.getCheckOutTime()).isNull();
        // Same open row was updated in place (false first entry replaced).
        assertThat(logs.get(0).getId()).isEqualTo(finalOpen.getId());
    }

    @Test
    void syncAllDevices_duplicateExit_extendsLastCheckout() {
        DeviceConfig entryDevice = device(10L, "101", "ENTRY");
        DeviceConfig exitDevice = device(11L, "102", "EXIT");
        Employee employee = employee(5L, "1001");

        stubDevicesAndEmployee(entryDevice, exitDevice, employee);
        when(attendanceLogRepository.findFirstByTenantIdAndEmployeeIdAndCheckOutTimeIsNullOrderByCheckInTimeDesc(1L, 5L))
                .thenReturn(Optional.empty());
        when(attendanceLogRepository.findByTenantIdAndEmployeeIdAndCheckInTime(eq(1L), eq(5L), any()))
                .thenReturn(Optional.empty());
        when(attendanceLogRepository.save(any(AttendanceLog.class))).thenAnswer(invocation -> {
            AttendanceLog log = invocation.getArgument(0);
            if (log.getId() == null) {
                log.setId(logIdSeq.getAndIncrement());
            }
            return log;
        });

        LocalDate day = LocalDate.of(2026, 7, 1);
        when(attendanceLogSyncService.getAttendanceLogs(eq(101L), isNull(), any(), any(), eq(0), anyInt()))
                .thenReturn(List.of(punch("1001", 101L, day.atTime(9, 0))));
        when(attendanceLogSyncService.getAttendanceLogs(eq(102L), isNull(), any(), any(), eq(0), anyInt()))
                .thenReturn(List.of(
                        punch("1001", 102L, day.atTime(17, 0)),
                        punch("1001", 102L, day.atTime(17, 30))
                ));

        doorAttendanceSyncService.syncAllDevices(day.atStartOfDay(), day.plusDays(1).atStartOfDay(), 100);

        ArgumentCaptor<AttendanceLog> saved = ArgumentCaptor.forClass(AttendanceLog.class);
        verify(attendanceLogRepository, atLeastOnce()).save(saved.capture());
        List<AttendanceLog> logs = saved.getAllValues();

        AttendanceLog closed = logs.get(logs.size() - 1);
        assertThat(closed.getCheckInTime()).isEqualTo(day.atTime(9, 0));
        assertThat(closed.getCheckOutTime()).isEqualTo(day.atTime(17, 30));
    }

    @Test
    void syncAllDevices_entryExitPair_closesSessionNormally() {
        DeviceConfig entryDevice = device(10L, "101", "ENTRY");
        DeviceConfig exitDevice = device(11L, "102", "EXIT");
        Employee employee = employee(5L, "1001");

        stubDevicesAndEmployee(entryDevice, exitDevice, employee);
        when(attendanceLogRepository.findFirstByTenantIdAndEmployeeIdAndCheckOutTimeIsNullOrderByCheckInTimeDesc(1L, 5L))
                .thenReturn(Optional.empty());
        when(attendanceLogRepository.findByTenantIdAndEmployeeIdAndCheckInTime(eq(1L), eq(5L), any()))
                .thenReturn(Optional.empty());
        when(attendanceLogRepository.save(any(AttendanceLog.class))).thenAnswer(invocation -> {
            AttendanceLog log = invocation.getArgument(0);
            if (log.getId() == null) {
                log.setId(logIdSeq.getAndIncrement());
            }
            return log;
        });

        LocalDate day = LocalDate.of(2026, 7, 1);
        when(attendanceLogSyncService.getAttendanceLogs(eq(101L), isNull(), any(), any(), eq(0), anyInt()))
                .thenReturn(List.of(punch("1001", 101L, day.atTime(9, 0))));
        when(attendanceLogSyncService.getAttendanceLogs(eq(102L), isNull(), any(), any(), eq(0), anyInt()))
                .thenReturn(List.of(punch("1001", 102L, day.atTime(18, 0))));

        DoorAttendanceSyncResultDTO result = doorAttendanceSyncService.syncAllDevices(
                day.atStartOfDay(), day.plusDays(1).atStartOfDay(), 100);

        assertThat(result.getMatchedSessions()).isEqualTo(1);
        ArgumentCaptor<AttendanceLog> saved = ArgumentCaptor.forClass(AttendanceLog.class);
        verify(attendanceLogRepository, atLeastOnce()).save(saved.capture());
        AttendanceLog last = saved.getAllValues().get(saved.getAllValues().size() - 1);
        assertThat(last.getCheckInTime()).isEqualTo(day.atTime(9, 0));
        assertThat(last.getCheckOutTime()).isEqualTo(day.atTime(18, 0));
        verify(attendanceCalculationService).calculateForDay(eq(5L), eq(day));
        verify(attendanceService).generateDailySummary(eq(5L), eq(day));
    }

    @Test
    void syncAllDevices_exitWithoutEntry_isIgnored() {
        DeviceConfig entryDevice = device(10L, "101", "ENTRY");
        DeviceConfig exitDevice = device(11L, "102", "EXIT");
        Employee employee = employee(5L, "1001");

        stubDevicesAndEmployee(entryDevice, exitDevice, employee);
        when(attendanceLogRepository.findFirstByTenantIdAndEmployeeIdAndCheckOutTimeIsNullOrderByCheckInTimeDesc(1L, 5L))
                .thenReturn(Optional.empty());
        when(attendanceLogRepository.findFirstByTenantIdAndEmployeeIdAndCheckOutTimeIsNotNullOrderByCheckOutTimeDesc(1L, 5L))
                .thenReturn(Optional.empty());

        LocalDate day = LocalDate.of(2026, 7, 1);
        when(attendanceLogSyncService.getAttendanceLogs(eq(101L), isNull(), any(), any(), eq(0), anyInt()))
                .thenReturn(List.of());
        when(attendanceLogSyncService.getAttendanceLogs(eq(102L), isNull(), any(), any(), eq(0), anyInt()))
                .thenReturn(List.of(punch("1001", 102L, day.atTime(18, 0))));

        doorAttendanceSyncService.syncAllDevices(day.atStartOfDay(), day.plusDays(1).atStartOfDay(), 100);

        verify(attendanceLogRepository, never()).save(any(AttendanceLog.class));
    }

    private void stubDevicesAndEmployee(DeviceConfig entryDevice, DeviceConfig exitDevice, Employee employee) {
        when(deviceConfigRepository.findByTenantId(1L)).thenReturn(List.of(entryDevice, exitDevice));
        when(employeeRepository.findByDeviceAccessAndDeviceEmployeeNo(anyLong(), eq("1001")))
                .thenReturn(List.of(employee));
        when(employeeRepository.findByTenantIdAndId(1L, employee.getId())).thenReturn(Optional.of(employee));
    }

    private static DeviceConfig device(Long backendId, String isapiId, String role) {
        DeviceConfig device = new DeviceConfig();
        device.setId(backendId);
        device.setTenantId(1L);
        device.setDeviceId(isapiId);
        device.setDeviceIp("10.0.0.1");
        device.setStatus("ACTIVE");
        device.setDoorRole(role);
        return device;
    }

    private static Employee employee(Long id, String deviceEmployeeNo) {
        Employee employee = new Employee();
        employee.setId(id);
        employee.setTenantId(1L);
        employee.setEmployeeId("EMP-" + id);
        employee.setDeviceEmployeeNo(deviceEmployeeNo);
        return employee;
    }

    private static AttendanceLogSyncDTO.AttendanceLogEntryDTO punch(String employeeNo, Long isapiDeviceId, LocalDateTime localTime) {
        AttendanceLogSyncDTO.AttendanceLogEntryDTO punch = new AttendanceLogSyncDTO.AttendanceLogEntryDTO();
        punch.setEmployeeNo(employeeNo);
        punch.setDeviceId(isapiDeviceId);
        punch.setPunchTime(localTime.atZone(ZoneId.systemDefault()).toOffsetDateTime());
        return punch;
    }
}
