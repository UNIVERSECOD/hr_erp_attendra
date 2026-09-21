package com.hic.service;

import com.hic.dto.AttendanceReportRowDTO;
import com.hic.dto.PaginatedResponse;
import com.hic.model.AttendanceLog;
import com.hic.model.Employee;
import com.hic.model.Timetable;
import com.hic.repository.AttendanceLogRepository;
import com.hic.repository.DepartmentRepository;
import com.hic.repository.EmployeeRepository;
import com.hic.repository.FaceDataRepository;
import com.hic.repository.PositionRepository;
import com.hic.repository.TimetableRepository;
import com.hic.util.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
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
class AttendanceReportServiceTest {

    @Mock private AttendanceLogRepository attendanceLogRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private PositionRepository positionRepository;
    @Mock private FaceDataRepository faceDataRepository;
    @Mock private TimetableRepository timetableRepository;
    @Mock private AttendanceInferenceService attendanceInferenceService;
    @Mock private EmployeeShiftResolver employeeShiftResolver;

    @InjectMocks
    private AttendanceReportService attendanceReportService;

    @BeforeEach
    void setTenant() {
        TenantContext.setTenantId(1L);
        // Pass-through unless a test stubs a tighter behavior.
        lenient().when(attendanceInferenceService.dedupeSessions(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(attendanceInferenceService.inferDay(any(), any())).thenAnswer(invocation ->
                new AttendanceInferenceService().inferDay(invocation.getArgument(0), invocation.getArgument(1)));
        lenient().when(employeeShiftResolver.loadAssignmentsByEmployee(any(), any(), any(), any()))
                .thenReturn(java.util.Map.of());
        lenient().when(employeeShiftResolver.collectTimetableIds(any(), any())).thenAnswer(invocation -> {
            Iterable<Employee> employees = invocation.getArgument(0);
            java.util.Set<Long> ids = new java.util.HashSet<>();
            if (employees != null) {
                for (Employee employee : employees) {
                    if (employee.getTimetableId() != null) {
                        ids.add(employee.getTimetableId());
                    }
                }
            }
            return ids;
        });
        lenient().when(employeeShiftResolver.resolveFromCache(any(), any(), any(), any())).thenAnswer(invocation -> {
            Employee employee = invocation.getArgument(0);
            if (employee == null) {
                return new EmployeeShiftResolver.ResolvedShift(null, null, false);
            }
            @SuppressWarnings("unchecked")
            java.util.Map<Long, Timetable> timetableMap = invocation.getArgument(3);
            Long timetableId = employee.getTimetableId();
            Timetable timetable = timetableMap != null && timetableId != null ? timetableMap.get(timetableId) : null;
            String shiftType = timetable != null && timetable.getShiftType() != null && !timetable.getShiftType().isBlank()
                    ? timetable.getShiftType()
                    : employee.getShiftType();
            return new EmployeeShiftResolver.ResolvedShift(timetableId, shiftType, false);
        });
        lenient().when(employeeShiftResolver.resolveForLog(any(), any(), any(), any())).thenAnswer(invocation -> {
            Employee employee = invocation.getArgument(0);
            AttendanceLog log = invocation.getArgument(1);
            if (log != null && log.getShiftType() != null && !log.getShiftType().isBlank()) {
                return new EmployeeShiftResolver.ResolvedShift(log.getTimetableId(), log.getShiftType(), true);
            }
            if (employee == null) {
                return new EmployeeShiftResolver.ResolvedShift(null, null, false);
            }
            @SuppressWarnings("unchecked")
            java.util.Map<Long, Timetable> timetableMap = invocation.getArgument(3);
            Long timetableId = employee.getTimetableId();
            Timetable timetable = timetableMap != null && timetableId != null ? timetableMap.get(timetableId) : null;
            String shiftType = timetable != null && timetable.getShiftType() != null && !timetable.getShiftType().isBlank()
                    ? timetable.getShiftType()
                    : employee.getShiftType();
            // Snapshot-only is unmarked: do not retroactively collapse after schedule changes.
            return new EmployeeShiftResolver.ResolvedShift(timetableId, shiftType, false);
        });
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void getReport_filtersByAssignedWorkScheduleShiftType() {
        LocalDate day = LocalDate.of(2026, 7, 1);

        Employee flexibleEmployee = employee(1L, "AYK-1", "Aykhan", "Alakbarov", 10L, null);
        Employee standardEmployee = employee(2L, "STD-1", "Sara", "Standard", 11L, "STANDARD");

        Timetable flexibleTimetable = new Timetable();
        flexibleTimetable.setId(10L);
        flexibleTimetable.setShiftType("FLEXIBLE");

        Timetable standardTimetable = new Timetable();
        standardTimetable.setId(11L);
        standardTimetable.setShiftType("STANDARD");

        AttendanceLog flexibleLog = log(1L, day.atTime(9, 0), day.atTime(18, 0));
        AttendanceLog standardLog = log(2L, day.atTime(9, 0), day.atTime(18, 0));

        when(attendanceLogRepository.findByTenantIdAndCheckInTimeBetween(eq(1L), any(), any()))
                .thenReturn(List.of(flexibleLog, standardLog));
        when(employeeRepository.findAllById(any())).thenReturn(List.of(flexibleEmployee, standardEmployee));
        when(timetableRepository.findAllById(any())).thenReturn(List.of(flexibleTimetable, standardTimetable));
        when(departmentRepository.findAllById(any())).thenReturn(List.of());
        when(positionRepository.findAllById(any())).thenReturn(List.of());
        when(faceDataRepository.findTopByEmployeeIdOrderByCreatedAtDesc(any())).thenReturn(Optional.empty());
        when(attendanceInferenceService.overlapsDay(any(), any())).thenReturn(true);

        PaginatedResponse<AttendanceReportRowDTO> all = attendanceReportService.getReport(
                day, day, "", null, null, null, null, null, null, 0, 50);
        PaginatedResponse<AttendanceReportRowDTO> flexibleOnly = attendanceReportService.getReport(
                day, day, "FLEXIBLE", null, null, null, null, null, null, 0, 50);
        PaginatedResponse<AttendanceReportRowDTO> standardOnly = attendanceReportService.getReport(
                day, day, "STANDARD", null, null, null, null, null, null, 0, 50);
        PaginatedResponse<AttendanceReportRowDTO> nightOnly = attendanceReportService.getReport(
                day, day, "NIGHT", null, null, null, null, null, null, 0, 50);

        assertThat(all.getContent()).extracting(AttendanceReportRowDTO::getFullName)
                .containsExactlyInAnyOrder("Aykhan Alakbarov", "Sara Standard");
        assertThat(flexibleOnly.getContent()).extracting(AttendanceReportRowDTO::getFullName)
                .containsExactly("Aykhan Alakbarov");
        assertThat(standardOnly.getContent()).extracting(AttendanceReportRowDTO::getFullName)
                .containsExactly("Sara Standard");
        assertThat(nightOnly.getContent()).isEmpty();

        assertThat(flexibleOnly.getContent().get(0).getShiftType()).isEqualTo("FLEXIBLE");
    }

    @Test
    void getReport_returnsOneRowPerCheckInOutPair() {
        LocalDate day = LocalDate.of(2026, 7, 27);
        Employee employee = employee(1L, "AYK-1", "Aykhan", "Alakbarov", 10L, "FLEXIBLE");

        Timetable timetable = new Timetable();
        timetable.setId(10L);
        timetable.setShiftType("FLEXIBLE");

        List<AttendanceLog> punches = List.of(
                log(1L, day.atTime(22, 44), day.atTime(22, 46)),
                log(1L, day.atTime(22, 47), day.atTime(22, 48)),
                log(1L, day.atTime(23, 12), day.atTime(23, 16)),
                log(1L, day.atTime(23, 28), null)
        );

        when(attendanceLogRepository.findByTenantIdAndCheckInTimeBetween(eq(1L), any(), any()))
                .thenReturn(punches);
        when(employeeRepository.findAllById(any())).thenReturn(List.of(employee));
        when(timetableRepository.findAllById(any())).thenReturn(List.of(timetable));
        when(departmentRepository.findAllById(any())).thenReturn(List.of());
        when(positionRepository.findAllById(any())).thenReturn(List.of());
        when(faceDataRepository.findTopByEmployeeIdOrderByCreatedAtDesc(any())).thenReturn(Optional.empty());
        when(attendanceInferenceService.overlapsDay(any(), any())).thenReturn(true);

        PaginatedResponse<AttendanceReportRowDTO> rows = attendanceReportService.getReport(
                day, day, "", null, null, null, null, null, null, 0, 50);

        assertThat(rows.getContent()).hasSize(4);
        assertThat(rows.getContent().get(0).getCheckInTime().toLocalDateTime()).isEqualTo(day.atTime(22, 44));
        assertThat(rows.getContent().get(0).getCheckOutTime().toLocalDateTime()).isEqualTo(day.atTime(22, 46));
        assertThat(rows.getContent().get(0).getWorkedMinutes()).isEqualTo(2);
        assertThat(rows.getContent().get(1).getCheckInTime().toLocalDateTime()).isEqualTo(day.atTime(22, 47));
        assertThat(rows.getContent().get(2).getCheckInTime().toLocalDateTime()).isEqualTo(day.atTime(23, 12));
        assertThat(rows.getContent().get(3).getCheckInTime().toLocalDateTime()).isEqualTo(day.atTime(23, 28));
        assertThat(rows.getContent().get(3).getCheckOutTime()).isNull();
        assertThat(rows.getContent().get(3).getWorkedMinutes()).isZero();
    }

    @Test
    void getReport_acceptsLegacyFilterAliases() {
        LocalDate day = LocalDate.of(2026, 7, 1);
        Employee employee = employee(1L, "AYK-1", "Aykhan", "Alakbarov", 10L, "FIRST_ENTRY");

        Timetable timetable = new Timetable();
        timetable.setId(10L);
        timetable.setShiftType("FLEXIBLE");

        when(attendanceLogRepository.findByTenantIdAndCheckInTimeBetween(eq(1L), any(), any()))
                .thenReturn(List.of(log(1L, day.atTime(10, 0), day.atTime(14, 0))));
        when(employeeRepository.findAllById(any())).thenReturn(List.of(employee));
        when(timetableRepository.findAllById(any())).thenReturn(List.of(timetable));
        when(departmentRepository.findAllById(any())).thenReturn(List.of());
        when(positionRepository.findAllById(any())).thenReturn(List.of());
        when(faceDataRepository.findTopByEmployeeIdOrderByCreatedAtDesc(any())).thenReturn(Optional.empty());
        when(attendanceInferenceService.overlapsDay(any(), any())).thenReturn(true);

        PaginatedResponse<AttendanceReportRowDTO> rows = attendanceReportService.getReport(
                day, day, "FIRST_ENTRY", null, null, null, null, null, null, 0, 50);

        assertThat(rows.getContent()).hasSize(1);
        assertThat(rows.getContent().get(0).getShiftType()).isEqualTo("FLEXIBLE");
    }

    @Test
    void getReport_includesNightShiftSessionOverlappingSelectedDay() {
        LocalDate day = LocalDate.of(2026, 7, 28);
        Employee employee = employee(1L, "NGT-1", "Night", "Worker", 12L, "NIGHT");

        Timetable timetable = new Timetable();
        timetable.setId(12L);
        timetable.setShiftType("NIGHT");

        AttendanceLog overnight = log(1L, day.minusDays(1).atTime(22, 0), day.atTime(6, 0));

        when(attendanceLogRepository.findByTenantIdAndCheckInTimeBetween(eq(1L), any(), any()))
                .thenReturn(List.of(overnight));
        when(employeeRepository.findAllById(any())).thenReturn(List.of(employee));
        when(timetableRepository.findAllById(any())).thenReturn(List.of(timetable));
        when(departmentRepository.findAllById(any())).thenReturn(List.of());
        when(positionRepository.findAllById(any())).thenReturn(List.of());
        when(faceDataRepository.findTopByEmployeeIdOrderByCreatedAtDesc(any())).thenReturn(Optional.empty());
        when(attendanceInferenceService.overlapsDay(any(), eq(day))).thenReturn(true);

        PaginatedResponse<AttendanceReportRowDTO> rows = attendanceReportService.getReport(
                day, day, "NIGHT", null, null, null, null, null, null, 0, 50);

        assertThat(rows.getContent()).hasSize(1);
        assertThat(rows.getContent().get(0).getCheckInTime().toLocalDateTime())
                .isEqualTo(day.minusDays(1).atTime(22, 0));
        assertThat(rows.getContent().get(0).getWorkedMinutes()).isEqualTo(8 * 60);
    }

    @Test
    void getReport_collapsesDuplicateSessionsFromSyncRace() {
        LocalDate day = LocalDate.of(2026, 7, 28);
        Employee employee = employee(1L, "HO-1", "Leyla", "-", 10L, "FLEXIBLE");

        Timetable timetable = new Timetable();
        timetable.setId(10L);
        timetable.setShiftType("FLEXIBLE");

        LocalDateTime in = day.atTime(12, 55);
        LocalDateTime out = day.atTime(14, 54);
        AttendanceLog first = log(1L, in, out);
        first.setId(101L);
        AttendanceLog duplicateA = log(1L, in, out);
        duplicateA.setId(102L);
        AttendanceLog duplicateB = log(1L, in, out);
        duplicateB.setId(103L);
        AttendanceLog other = log(1L, day.atTime(0, 4), day.atTime(3, 18));
        other.setId(100L);

        when(attendanceLogRepository.findByTenantIdAndCheckInTimeBetween(eq(1L), any(), any()))
                .thenReturn(List.of(first, duplicateA, duplicateB, other));
        when(employeeRepository.findAllById(any())).thenReturn(List.of(employee));
        when(timetableRepository.findAllById(any())).thenReturn(List.of(timetable));
        when(departmentRepository.findAllById(any())).thenReturn(List.of());
        when(positionRepository.findAllById(any())).thenReturn(List.of());
        when(faceDataRepository.findTopByEmployeeIdOrderByCreatedAtDesc(any())).thenReturn(Optional.empty());
        when(attendanceInferenceService.overlapsDay(any(), any())).thenReturn(true);
        when(attendanceInferenceService.dedupeSessions(any())).thenAnswer(invocation -> {
            List<AttendanceLog> logs = invocation.getArgument(0);
            return new AttendanceInferenceService().dedupeSessions(logs);
        });

        PaginatedResponse<AttendanceReportRowDTO> rows = attendanceReportService.getReport(
                day, day, "", null, null, null, null, null, null, 0, 50);

        assertThat(rows.getContent()).hasSize(2);
        assertThat(rows.getContent())
                .extracting(AttendanceReportRowDTO::getAttendanceLogId)
                .containsExactlyInAnyOrder(100L, 101L);
    }

    @Test
    void getReport_standardShiftCollapsesToOneRowPerDay() {
        LocalDate day = LocalDate.of(2026, 7, 28);
        Employee employee = employee(1L, "STD-1", "Sara", "Standard", 11L, "STANDARD");

        Timetable timetable = new Timetable();
        timetable.setId(11L);
        timetable.setShiftType("STANDARD");

        AttendanceLog first = log(1L, day.atTime(9, 0), day.atTime(12, 0));
        first.setId(1L);
        first.setShiftType("STANDARD");
        AttendanceLog second = log(1L, day.atTime(13, 0), day.atTime(18, 0));
        second.setId(2L);
        second.setShiftType("STANDARD");

        when(attendanceLogRepository.findByTenantIdAndCheckInTimeBetween(eq(1L), any(), any()))
                .thenReturn(List.of(first, second));
        when(employeeRepository.findAllById(any())).thenReturn(List.of(employee));
        when(timetableRepository.findAllById(any())).thenReturn(List.of(timetable));
        when(departmentRepository.findAllById(any())).thenReturn(List.of());
        when(positionRepository.findAllById(any())).thenReturn(List.of());
        when(faceDataRepository.findTopByEmployeeIdOrderByCreatedAtDesc(any())).thenReturn(Optional.empty());
        when(attendanceInferenceService.overlapsDay(any(), eq(day))).thenReturn(true);
        when(attendanceInferenceService.inferDay(any(), eq(day))).thenAnswer(invocation ->
                new AttendanceInferenceService().inferDay(invocation.getArgument(0), day));

        PaginatedResponse<AttendanceReportRowDTO> rows = attendanceReportService.getReport(
                day, day, "STANDARD", null, null, null, null, null, null, 0, 50);

        assertThat(rows.getContent()).hasSize(1);
        assertThat(rows.getContent().get(0).getCheckInTime().toLocalDateTime()).isEqualTo(day.atTime(9, 0));
        assertThat(rows.getContent().get(0).getCheckOutTime().toLocalDateTime()).isEqualTo(day.atTime(18, 0));
        assertThat(rows.getContent().get(0).getWorkedMinutes()).isEqualTo(9 * 60);
    }

    @Test
    void getReport_keepsHistoricalFlexibleRowsAfterScheduleChange() {
        LocalDate flexibleDay = LocalDate.of(2026, 7, 20);
        LocalDate standardDay = LocalDate.of(2026, 7, 28);
        Employee employee = employee(1L, "HO-1", "Leyla", "-", 11L, "STANDARD");

        Timetable standardTimetable = new Timetable();
        standardTimetable.setId(11L);
        standardTimetable.setShiftType("STANDARD");

        AttendanceLog flexibleLog = log(1L, flexibleDay.atTime(10, 0), flexibleDay.atTime(11, 0));
        flexibleLog.setId(10L);
        AttendanceLog standardMorning = log(1L, standardDay.atTime(9, 0), standardDay.atTime(12, 0));
        standardMorning.setId(20L);
        AttendanceLog standardAfternoon = log(1L, standardDay.atTime(13, 0), standardDay.atTime(18, 0));
        standardAfternoon.setId(21L);

        when(attendanceLogRepository.findByTenantIdAndCheckInTimeBetween(eq(1L), any(), any()))
                .thenReturn(List.of(flexibleLog, standardMorning, standardAfternoon));
        when(employeeRepository.findAllById(any())).thenReturn(List.of(employee));
        when(timetableRepository.findAllById(any())).thenReturn(List.of(standardTimetable));
        when(departmentRepository.findAllById(any())).thenReturn(List.of());
        when(positionRepository.findAllById(any())).thenReturn(List.of());
        when(faceDataRepository.findTopByEmployeeIdOrderByCreatedAtDesc(any())).thenReturn(Optional.empty());
        when(attendanceInferenceService.overlapsDay(any(), any())).thenAnswer(invocation -> {
            AttendanceLog log = invocation.getArgument(0);
            LocalDate day = invocation.getArgument(1);
            return new AttendanceInferenceService().overlapsDay(log, day);
        });
        when(attendanceInferenceService.inferDay(any(), eq(standardDay))).thenAnswer(invocation ->
                new AttendanceInferenceService().inferDay(invocation.getArgument(0), standardDay));
        org.mockito.Mockito.doAnswer(invocation -> {
            AttendanceLog punched = invocation.getArgument(1);
            LocalDate date = punched != null && punched.getCheckInTime() != null
                    ? punched.getCheckInTime().toLocalDate()
                    : null;
            if (date != null && !date.isAfter(LocalDate.of(2026, 7, 25))) {
                return new EmployeeShiftResolver.ResolvedShift(10L, "FLEXIBLE", true);
            }
            return new EmployeeShiftResolver.ResolvedShift(11L, "STANDARD", true);
        }).when(employeeShiftResolver).resolveForLog(any(), any(), any(), any());

        PaginatedResponse<AttendanceReportRowDTO> flexibleOnly = attendanceReportService.getReport(
                flexibleDay, standardDay, "FLEXIBLE", null, null, null, null, null, null, 0, 50);
        PaginatedResponse<AttendanceReportRowDTO> standardOnly = attendanceReportService.getReport(
                flexibleDay, standardDay, "STANDARD", null, null, null, null, null, null, 0, 50);

        assertThat(flexibleOnly.getContent()).hasSize(1);
        assertThat(flexibleOnly.getContent().get(0).getDate()).isEqualTo(flexibleDay);
        assertThat(standardOnly.getContent()).hasSize(1);
        assertThat(standardOnly.getContent().get(0).getDate()).isEqualTo(standardDay);
        assertThat(standardOnly.getContent().get(0).getCheckInTime().toLocalDateTime()).isEqualTo(standardDay.atTime(9, 0));
        assertThat(standardOnly.getContent().get(0).getCheckOutTime().toLocalDateTime()).isEqualTo(standardDay.atTime(18, 0));
    }

    @Test
    void getReport_keepsUnstampedSessionsVisibleAfterScheduleChangeToStandard() {
        LocalDate day = LocalDate.of(2026, 7, 20);
        // Employee currently STANDARD, but old punches have no stamp/history → must stay visible.
        Employee employee = employee(1L, "HO-1", "Leyla", "-", 11L, "STANDARD");

        Timetable timetable = new Timetable();
        timetable.setId(11L);
        timetable.setShiftType("STANDARD");

        AttendanceLog morning = log(1L, day.atTime(9, 0), day.atTime(10, 0));
        morning.setId(1L);
        AttendanceLog afternoon = log(1L, day.atTime(14, 0), day.atTime(15, 0));
        afternoon.setId(2L);

        when(attendanceLogRepository.findByTenantIdAndCheckInTimeBetween(eq(1L), any(), any()))
                .thenReturn(List.of(morning, afternoon));
        when(employeeRepository.findAllById(any())).thenReturn(List.of(employee));
        when(timetableRepository.findAllById(any())).thenReturn(List.of(timetable));
        when(departmentRepository.findAllById(any())).thenReturn(List.of());
        when(positionRepository.findAllById(any())).thenReturn(List.of());
        when(faceDataRepository.findTopByEmployeeIdOrderByCreatedAtDesc(any())).thenReturn(Optional.empty());
        when(attendanceInferenceService.overlapsDay(any(), any())).thenReturn(true);

        PaginatedResponse<AttendanceReportRowDTO> all = attendanceReportService.getReport(
                day, day, "", null, null, null, null, null, null, 0, 50);

        assertThat(all.getContent()).hasSize(2);
        assertThat(all.getContent()).extracting(AttendanceReportRowDTO::getAttendanceLogId)
                .containsExactlyInAnyOrder(1L, 2L);
    }

    private static Employee employee(Long id, String code, String first, String last, Long timetableId, String shiftType) {
        Employee employee = new Employee();
        employee.setId(id);
        employee.setTenantId(1L);
        employee.setEmployeeId(code);
        employee.setFirstName(first);
        employee.setLastName(last);
        employee.setTimetableId(timetableId);
        employee.setShiftType(shiftType);
        return employee;
    }

    private static AttendanceLog log(Long employeeId, LocalDateTime in, LocalDateTime out) {
        AttendanceLog log = new AttendanceLog();
        log.setEmployeeId(employeeId);
        log.setTenantId(1L);
        log.setCheckInTime(in);
        log.setCheckOutTime(out);
        return log;
    }
}
