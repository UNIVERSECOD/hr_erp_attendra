package com.hic.service;

import com.hic.model.AttendanceLog;
import com.hic.model.AttendanceRecord;
import com.hic.model.Employee;
import com.hic.repository.AttendanceLogRepository;
import com.hic.repository.AttendanceRecordRepository;
import com.hic.repository.EmployeeRepository;
import com.hic.util.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceCalculationServiceTest {

    @Mock
    private AttendanceLogRepository attendanceLogRepository;

    @Mock
    private AttendanceRecordRepository attendanceRecordRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private HolidayService holidayService;

    @Mock
    private LeaveService leaveService;

    @Spy
    private AttendanceInferenceService attendanceInferenceService = new AttendanceInferenceService();

    @Mock
    private EmployeeShiftResolver employeeShiftResolver;

    @InjectMocks
    private AttendanceCalculationService attendanceCalculationService;

    @BeforeEach
    void setTenant() {
        TenantContext.setTenantId(7L);
        lenient().when(employeeShiftResolver.resolve(any(), any())).thenAnswer(invocation -> {
            Employee employee = invocation.getArgument(0);
            return new EmployeeShiftResolver.ResolvedShift(employee.getTimetableId(), employee.getShiftType());
        });
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void calculateForDay_multiPunchDay_standardShiftUsesFirstInLastOutSpan() {
        LocalDate workDate = LocalDate.of(2024, 1, 15);

        Employee employee = new Employee();
        employee.setId(1L);
        employee.setTenantId(7L);
        employee.setShiftType("STANDARD");

        AttendanceLog morning = new AttendanceLog();
        morning.setEmployeeId(1L);
        morning.setCheckInTime(LocalDateTime.of(2024, 1, 15, 9, 0));
        morning.setCheckOutTime(LocalDateTime.of(2024, 1, 15, 13, 0));

        AttendanceLog noon = new AttendanceLog();
        noon.setEmployeeId(1L);
        noon.setCheckInTime(LocalDateTime.of(2024, 1, 15, 14, 0));
        noon.setCheckOutTime(LocalDateTime.of(2024, 1, 15, 14, 50));

        AttendanceLog evening = new AttendanceLog();
        evening.setEmployeeId(1L);
        evening.setCheckInTime(LocalDateTime.of(2024, 1, 15, 15, 0));
        evening.setCheckOutTime(LocalDateTime.of(2024, 1, 15, 17, 0));

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(attendanceRecordRepository.findByEmployeeIdAndWorkDate(1L, workDate)).thenReturn(Optional.empty());
        when(attendanceLogRepository.findByEmployeeIdAndCheckInTimeBetween(eq(1L), any(), any()))
                .thenReturn(List.of(morning, noon, evening));
        when(leaveService.hasActiveLeave(1L, workDate)).thenReturn(false);
        when(holidayService.isHoliday(workDate)).thenReturn(false);
        when(attendanceRecordRepository.save(any(AttendanceRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AttendanceRecord record = attendanceCalculationService.calculateForDay(1L, workDate);

        assertThat(record.getEntryTime()).isEqualTo(LocalDateTime.of(2024, 1, 15, 9, 0));
        assertThat(record.getExitTime()).isEqualTo(LocalDateTime.of(2024, 1, 15, 17, 0));
        assertThat(record.getWorkedMinutes()).isEqualTo(480);
        assertThat(record.getStatus()).isEqualTo("PRESENT");
        assertThat(record.getTenantId()).isEqualTo(7L);
    }

    @Test
    void calculateForDay_multiPunchDay_flexibleShiftSumsIntervals() {
        LocalDate workDate = LocalDate.of(2024, 1, 15);

        Employee employee = new Employee();
        employee.setId(1L);
        employee.setTenantId(7L);
        employee.setShiftType("FLEXIBLE");

        AttendanceLog morning = new AttendanceLog();
        morning.setEmployeeId(1L);
        morning.setCheckInTime(LocalDateTime.of(2024, 1, 15, 9, 0));
        morning.setCheckOutTime(LocalDateTime.of(2024, 1, 15, 11, 0));

        AttendanceLog midday = new AttendanceLog();
        midday.setEmployeeId(1L);
        midday.setCheckInTime(LocalDateTime.of(2024, 1, 15, 13, 0));
        midday.setCheckOutTime(LocalDateTime.of(2024, 1, 15, 15, 30));

        AttendanceLog evening = new AttendanceLog();
        evening.setEmployeeId(1L);
        evening.setCheckInTime(LocalDateTime.of(2024, 1, 15, 17, 0));
        evening.setCheckOutTime(LocalDateTime.of(2024, 1, 15, 18, 0));

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(attendanceRecordRepository.findByEmployeeIdAndWorkDate(1L, workDate)).thenReturn(Optional.empty());
        when(attendanceLogRepository.findByEmployeeIdAndCheckInTimeBetween(eq(1L), any(), any()))
                .thenReturn(List.of(morning, midday, evening));
        when(leaveService.hasActiveLeave(1L, workDate)).thenReturn(false);
        when(holidayService.isHoliday(workDate)).thenReturn(false);
        when(attendanceRecordRepository.save(any(AttendanceRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AttendanceRecord record = attendanceCalculationService.calculateForDay(1L, workDate);

        assertThat(record.getEntryTime()).isEqualTo(LocalDateTime.of(2024, 1, 15, 9, 0));
        assertThat(record.getExitTime()).isEqualTo(LocalDateTime.of(2024, 1, 15, 18, 0));
        // 2h + 2.5h + 1h = 330 minutes (gaps excluded)
        assertThat(record.getWorkedMinutes()).isEqualTo(330);
        assertThat(record.getStatus()).isEqualTo("PRESENT");
    }

    @Test
    void calculateForDay_withoutTenantContext_usesEmployeeTenant() {
        TenantContext.clear();
        LocalDate workDate = LocalDate.of(2024, 1, 15);

        Employee employee = new Employee();
        employee.setId(1L);
        employee.setTenantId(99L);

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(attendanceRecordRepository.findByEmployeeIdAndWorkDate(1L, workDate)).thenReturn(Optional.empty());
        when(attendanceLogRepository.findByEmployeeIdAndCheckInTimeBetween(eq(1L), any(), any()))
                .thenReturn(List.of());
        when(leaveService.hasActiveLeave(1L, workDate)).thenReturn(false);
        when(holidayService.isHoliday(workDate)).thenReturn(false);
        when(attendanceRecordRepository.save(any(AttendanceRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AttendanceRecord record = attendanceCalculationService.calculateForDay(1L, workDate);

        assertThat(record.getTenantId()).isEqualTo(99L);
        assertThat(record.getStatus()).isEqualTo("ABSENT");
    }
}
