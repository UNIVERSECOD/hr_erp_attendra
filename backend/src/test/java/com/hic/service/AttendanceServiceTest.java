package com.hic.service;

import com.hic.dto.AttendanceDTO;
import com.hic.dto.AttendanceLogDTO;
import com.hic.dto.DailyAttendanceSummaryDTO;
import com.hic.dto.EmployeeAttendanceRowDTO;
import com.hic.dto.EmployeeAttendanceSummaryDTO;
import com.hic.model.AttendanceLog;
import com.hic.model.DailyAttendanceSummary;
import com.hic.model.DailyAttendanceSummary.AttendanceStatus;
import com.hic.model.Employee;
import com.hic.model.EmployeePermission;
import com.hic.model.LeaveRequest;
import com.hic.model.Timetable;
import com.hic.repository.AttendanceLogRepository;
import com.hic.repository.DailyAttendanceSummaryRepository;
import com.hic.repository.EmployeePermissionRepository;
import com.hic.repository.EmployeeRepository;
import com.hic.repository.LeaveRequestRepository;
import com.hic.repository.TimetableRepository;
import com.hic.repository.WorkScheduleRepository;
import com.hic.util.AppTimeZone;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    @Mock
    private AttendanceLogRepository attendanceLogRepository;

    @Mock
    private DailyAttendanceSummaryRepository summaryRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private LeaveRequestRepository leaveRequestRepository;

    @Mock
    private EmployeePermissionRepository employeePermissionRepository;

    @Mock
    private TimetableRepository timetableRepository;

    @Mock
    private WorkScheduleRepository workScheduleRepository;

    @Mock
    private UserScopeService userScopeService;

    @Spy
    private AttendanceInferenceService attendanceInferenceService = new AttendanceInferenceService();

    @Mock
    private EmployeeShiftResolver employeeShiftResolver;

    @InjectMocks
    private AttendanceService attendanceService;

    private AttendanceLog testLog;
    private DailyAttendanceSummary testSummary;

    @BeforeEach
    void setUp() {
        lenient().when(employeeShiftResolver.resolve(any(), any())).thenAnswer(invocation -> {
            Employee employee = invocation.getArgument(0);
            if (employee == null) {
                return new EmployeeShiftResolver.ResolvedShift(null, null);
            }
            return new EmployeeShiftResolver.ResolvedShift(employee.getTimetableId(), employee.getShiftType());
        });

        testLog = new AttendanceLog();
        testLog.setId(1L);
        testLog.setEmployeeId(1L);
        testLog.setCheckInTime(LocalDateTime.of(2024, 1, 15, 9, 0));
        testLog.setCheckOutTime(LocalDateTime.of(2024, 1, 15, 18, 0));
        testLog.setStatus("ACTIVE");

        testSummary = new DailyAttendanceSummary();
        testSummary.setId(1L);
        testSummary.setEmployeeId(1L);
        testSummary.setAttendanceDate(LocalDate.of(2024, 1, 15));
        testSummary.setAttendanceStatus(AttendanceStatus.PRESENT);
        testSummary.setHoursWorked(9.0);

        lenient().when(userScopeService.resolveBranchScope(null)).thenReturn(null);
        lenient().when(employeeRepository.findById(1L)).thenReturn(Optional.empty());
    }

    @Test
    void logAttendance_validDTO_savesAndReturnsLog() {
        AttendanceDTO dto = new AttendanceDTO();
        dto.setEmployeeId(1L);
        dto.setCheckInTime(LocalDateTime.of(2024, 1, 15, 9, 0));
        dto.setCheckOutTime(LocalDateTime.of(2024, 1, 15, 18, 0));

        when(attendanceLogRepository.save(any(AttendanceLog.class))).thenReturn(testLog);
        when(attendanceLogRepository.findByEmployeeIdAndCheckInTimeBetween(eq(1L), any(), any()))
                .thenReturn(List.of(testLog));
        when(summaryRepository.findByEmployeeIdAndAttendanceDate(eq(1L), any()))
                .thenReturn(Optional.empty());
        when(summaryRepository.save(any(DailyAttendanceSummary.class))).thenReturn(testSummary);

        AttendanceLogDTO result = attendanceService.logAttendance(dto);

        assertThat(result).isNotNull();
        assertThat(result.getEmployeeId()).isEqualTo(1L);
        verify(attendanceLogRepository).save(any(AttendanceLog.class));
    }

    @Test
    void generateDailySummary_withLogs_calculatesPresentStatus() {
        LocalDate date = LocalDate.of(2024, 1, 15);
        when(attendanceLogRepository.findByEmployeeIdAndCheckInTimeBetween(eq(1L), any(), any()))
                .thenReturn(List.of(testLog));
        when(summaryRepository.findByEmployeeIdAndAttendanceDate(1L, date))
                .thenReturn(Optional.empty());
        when(summaryRepository.save(any(DailyAttendanceSummary.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DailyAttendanceSummaryDTO result = attendanceService.generateDailySummary(1L, date);

        assertThat(result).isNotNull();
        assertThat(result.getAttendanceStatus()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(result.getCheckInTime()).isNotNull();
        assertThat(result.getCheckOutTime()).isNotNull();
        assertThat(result.getHoursWorked()).isEqualTo(9.0);
    }

    @Test
    void generateDailySummary_noLogs_calculatesAbsentStatus() {
        LocalDate date = LocalDate.of(2024, 1, 15);
        DailyAttendanceSummary absentSummary = new DailyAttendanceSummary();
        absentSummary.setId(2L);
        absentSummary.setEmployeeId(1L);
        absentSummary.setAttendanceDate(date);
        absentSummary.setAttendanceStatus(AttendanceStatus.ABSENT);
        absentSummary.setHoursWorked(0.0);

        when(attendanceLogRepository.findByEmployeeIdAndCheckInTimeBetween(eq(1L), any(), any()))
                .thenReturn(List.of());
        when(summaryRepository.findByEmployeeIdAndAttendanceDate(1L, date))
                .thenReturn(Optional.empty());
        when(summaryRepository.save(any(DailyAttendanceSummary.class))).thenReturn(absentSummary);

        DailyAttendanceSummaryDTO result = attendanceService.generateDailySummary(1L, date);

        assertThat(result).isNotNull();
        assertThat(result.getAttendanceStatus()).isEqualTo(AttendanceStatus.ABSENT);
        assertThat(result.getHoursWorked()).isEqualTo(0.0);
    }

    @Test
    void getLogsForEmployee_returnsLogsInRange() {
        LocalDateTime start = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2024, 1, 31, 23, 59);

        when(attendanceLogRepository.findByEmployeeIdAndCheckInTimeBetween(1L, start, end))
                .thenReturn(List.of(testLog));

        List<AttendanceLogDTO> result = attendanceService.getLogsForEmployee(1L, start, end);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEmployeeId()).isEqualTo(1L);
    }

    @Test
    void getDailySummary_returnsRange() {
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate end = LocalDate.of(2024, 1, 31);

        when(summaryRepository.findByEmployeeIdAndAttendanceDateBetween(1L, start, end))
                .thenReturn(List.of(testSummary));

        List<DailyAttendanceSummaryDTO> result = attendanceService.getDailySummary(1L, start, end);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAttendanceStatus()).isEqualTo(AttendanceStatus.PRESENT);
    }

    @Test
    void getEmployeeAttendance_marksLeaveDaysAndLateDays() {
        Employee employee = new Employee();
        employee.setId(1L);
        employee.setBranchId(1L);
        employee.setTimetableId(3L);

        Timetable timetable = new Timetable();
        timetable.setId(3L);
        timetable.setStartTime(java.time.LocalTime.of(9, 0));
        timetable.setAllowedLateMinutes(10);

        AttendanceLog lateLog = new AttendanceLog();
        lateLog.setEmployeeId(1L);
        lateLog.setCheckInTime(LocalDateTime.of(2024, 1, 16, 9, 20));
        lateLog.setCheckOutTime(LocalDateTime.of(2024, 1, 16, 18, 0));

        LeaveRequest leaveRequest = new LeaveRequest();
        leaveRequest.setEmployeeId(1L);
        leaveRequest.setStartDate(LocalDate.of(2024, 1, 15));
        leaveRequest.setEndDate(LocalDate.of(2024, 1, 15));

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(attendanceLogRepository.findByEmployeeIdAndCheckInTimeBetween(eq(1L), any(), any()))
                .thenReturn(List.of(lateLog));
        when(summaryRepository.findByEmployeeIdAndAttendanceDateBetween(1L, LocalDate.of(2024, 1, 15), LocalDate.of(2024, 1, 16)))
                .thenReturn(List.of());
        when(leaveRequestRepository.findApprovedByEmployeeIdAndDateRange(1L, LocalDate.of(2024, 1, 15), LocalDate.of(2024, 1, 16)))
                .thenReturn(List.of(leaveRequest));
        when(employeePermissionRepository.findByEmployeeIdAndDateRange(1L, LocalDate.of(2024, 1, 15), LocalDate.of(2024, 1, 16)))
                .thenReturn(List.of());
        when(timetableRepository.findById(3L)).thenReturn(Optional.of(timetable));
        List<EmployeeAttendanceRowDTO> result = attendanceService.getEmployeeAttendance(
                1L, LocalDate.of(2024, 1, 15), LocalDate.of(2024, 1, 16));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getStatus()).isEqualTo(AttendanceStatus.ON_LEAVE);
        assertThat(result.get(1).getStatus()).isEqualTo(AttendanceStatus.LATE);
        assertThat(result.get(1).getHoursWorked()).isCloseTo(8.67, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void getEmployeeAttendanceSummary_countsStatuses() {
        Employee employee = new Employee();
        employee.setId(1L);
        employee.setBranchId(1L);

        AttendanceLog presentLog = new AttendanceLog();
        presentLog.setEmployeeId(1L);
        presentLog.setCheckInTime(LocalDateTime.of(2024, 1, 15, 9, 0));
        presentLog.setCheckOutTime(LocalDateTime.of(2024, 1, 15, 18, 0));

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(attendanceLogRepository.findByEmployeeIdAndCheckInTimeBetween(eq(1L), any(), any()))
                .thenReturn(List.of(presentLog));
        when(summaryRepository.findByEmployeeIdAndAttendanceDateBetween(1L, LocalDate.of(2024, 1, 15), LocalDate.of(2024, 1, 17)))
                .thenReturn(List.of());
        when(leaveRequestRepository.findApprovedByEmployeeIdAndDateRange(1L, LocalDate.of(2024, 1, 15), LocalDate.of(2024, 1, 17)))
                .thenReturn(List.of());
        when(employeePermissionRepository.findByEmployeeIdAndDateRange(1L, LocalDate.of(2024, 1, 15), LocalDate.of(2024, 1, 17)))
                .thenReturn(List.of());
        EmployeeAttendanceSummaryDTO result = attendanceService.getEmployeeAttendanceSummary(
                1L, LocalDate.of(2024, 1, 15), LocalDate.of(2024, 1, 17));

        assertThat(result.getTotalDays()).isEqualTo(3);
        assertThat(result.getWorkingDays()).isEqualTo(1);
        assertThat(result.getAbsentDays()).isEqualTo(2);
        assertThat(result.getTotalHours()).isEqualTo(9.0);
    }

    @Test
    void generateDailySummary_multiPunchDay_standardUsesFirstInLastOutSpan() {
        LocalDate date = LocalDate.of(2024, 1, 15);
        Employee employee = new Employee();
        employee.setId(1L);
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
        when(attendanceLogRepository.findByEmployeeIdAndCheckInTimeBetween(eq(1L), any(), any()))
                .thenReturn(List.of(morning, noon, evening));
        when(summaryRepository.findByEmployeeIdAndAttendanceDate(1L, date))
                .thenReturn(Optional.empty());
        when(summaryRepository.save(any(DailyAttendanceSummary.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DailyAttendanceSummaryDTO result = attendanceService.generateDailySummary(1L, date);

        assertThat(result.getCheckInTime()).isNotNull();
        assertThat(result.getCheckInTime().toLocalTime()).isEqualTo(java.time.LocalTime.of(9, 0));
        assertThat(result.getCheckOutTime()).isNotNull();
        assertThat(result.getCheckOutTime().toLocalTime()).isEqualTo(java.time.LocalTime.of(17, 0));
        assertThat(result.getHoursWorked()).isEqualTo(8.0);
        assertThat(result.getAttendanceStatus()).isEqualTo(AttendanceStatus.PRESENT);
    }

    @Test
    void generateDailySummary_multiPunchDay_flexibleSumsIntervalsAndIgnoresLate() {
        LocalDate date = LocalDate.of(2024, 1, 15);
        Employee employee = new Employee();
        employee.setId(1L);
        employee.setShiftType("FLEXIBLE");
        employee.setTimetableId(3L);

        Timetable timetable = new Timetable();
        timetable.setId(3L);
        timetable.setStartTime(java.time.LocalTime.of(9, 0));
        timetable.setAllowedLateMinutes(0);
        timetable.setShiftType("FLEXIBLE");

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
        when(timetableRepository.findById(3L)).thenReturn(Optional.of(timetable));
        when(attendanceLogRepository.findByEmployeeIdAndCheckInTimeBetween(eq(1L), any(), any()))
                .thenReturn(List.of(morning, midday, evening));
        when(summaryRepository.findByEmployeeIdAndAttendanceDate(1L, date))
                .thenReturn(Optional.empty());
        when(summaryRepository.save(any(DailyAttendanceSummary.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DailyAttendanceSummaryDTO result = attendanceService.generateDailySummary(1L, date);

        assertThat(result.getHoursWorked()).isEqualTo(5.5);
        assertThat(result.getAttendanceStatus()).isEqualTo(AttendanceStatus.PRESENT);
    }

    @Test
    void getEmployeeAttendance_flexibleShift_doesNotMarkLate() {
        Employee employee = new Employee();
        employee.setId(1L);
        employee.setBranchId(1L);
        employee.setTimetableId(3L);
        employee.setShiftType("FIRST_ENTRY");

        Timetable timetable = new Timetable();
        timetable.setId(3L);
        timetable.setStartTime(java.time.LocalTime.of(9, 0));
        timetable.setAllowedLateMinutes(0);
        timetable.setShiftType("FIRST_ENTRY");

        AttendanceLog lateLog = new AttendanceLog();
        lateLog.setEmployeeId(1L);
        lateLog.setCheckInTime(LocalDateTime.of(2024, 1, 16, 11, 0));
        lateLog.setCheckOutTime(LocalDateTime.of(2024, 1, 16, 15, 0));

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(attendanceLogRepository.findByEmployeeIdAndCheckInTimeBetween(eq(1L), any(), any()))
                .thenReturn(List.of(lateLog));
        when(summaryRepository.findByEmployeeIdAndAttendanceDateBetween(1L, LocalDate.of(2024, 1, 16), LocalDate.of(2024, 1, 16)))
                .thenReturn(List.of());
        when(leaveRequestRepository.findApprovedByEmployeeIdAndDateRange(1L, LocalDate.of(2024, 1, 16), LocalDate.of(2024, 1, 16)))
                .thenReturn(List.of());
        when(employeePermissionRepository.findByEmployeeIdAndDateRange(1L, LocalDate.of(2024, 1, 16), LocalDate.of(2024, 1, 16)))
                .thenReturn(List.of());
        when(timetableRepository.findById(3L)).thenReturn(Optional.of(timetable));

        List<EmployeeAttendanceRowDTO> result = attendanceService.getEmployeeAttendance(
                1L, LocalDate.of(2024, 1, 16), LocalDate.of(2024, 1, 16));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(result.get(0).getHoursWorked()).isEqualTo(4.0);
    }

    @Test
    void generateDailySummary_todayAfterFinalExit_marksWorkdayComplete() {
        LocalDate today = AppTimeZone.today();
        AttendanceLog firstSession = new AttendanceLog();
        firstSession.setEmployeeId(1L);
        firstSession.setCheckInTime(today.atTime(9, 0));
        firstSession.setCheckOutTime(today.atTime(13, 0));

        AttendanceLog secondSession = new AttendanceLog();
        secondSession.setEmployeeId(1L);
        secondSession.setCheckInTime(today.atTime(14, 0));
        secondSession.setCheckOutTime(today.atTime(17, 0));

        when(attendanceLogRepository.findByEmployeeIdAndCheckInTimeBetween(eq(1L), any(), any()))
                .thenReturn(List.of(firstSession, secondSession));
        when(summaryRepository.findByEmployeeIdAndAttendanceDate(1L, today))
                .thenReturn(Optional.empty());
        when(summaryRepository.save(any(DailyAttendanceSummary.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DailyAttendanceSummaryDTO result = attendanceService.generateDailySummary(1L, today);

        assertThat(result.getAttendanceStatus()).isEqualTo(AttendanceStatus.WORKDAY_COMPLETE);
        assertThat(result.getCheckOutTime()).isNotNull();
        assertThat(result.getCheckOutTime().toLocalTime()).isEqualTo(java.time.LocalTime.of(17, 0));
    }
}
