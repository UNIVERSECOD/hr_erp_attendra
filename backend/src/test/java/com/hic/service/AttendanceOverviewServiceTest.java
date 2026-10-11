package com.hic.service;

import com.hic.dto.*;
import com.hic.exception.BadRequestException;
import com.hic.model.*;
import com.hic.repository.*;
import com.hic.util.TenantContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.ByteArrayInputStream;
import java.time.*;
import java.util.*;
import java.util.function.BiFunction;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttendanceOverviewServiceTest {
    @Mock EmployeeRepository employees;
    @Mock EmployeeAreaRepository employeeAreas;
    @Mock AttendanceLogRepository logs;
    @Mock DepartmentRepository departments;
    @Mock PositionRepository positions;
    @Mock BranchRepository branches;
    @Mock TimetableRepository timetables;
    @Mock AttendanceService attendance;
    @Mock AttendanceReportService details;
    @Mock AttendanceScheduleResolver scheduleResolver;
    @Mock UserScopeService userScope;
    @InjectMocks AttendanceOverviewService service;
    Employee employee;
    LocalDate start = LocalDate.of(2026, 9, 1);
    AttendanceReportQuery query;

    @BeforeEach void setup() {
        TenantContext.setTenantId(7L);
        lenient().when(userScope.resolveBranchScope(null)).thenReturn(null);
        employee = new Employee(); employee.setId(1L); employee.setTenantId(7L);
        employee.setFirstName("İlham"); employee.setLastName("Test"); employee.setFinNumber("FIN1234");
        employee.setEmployeeId("HR1"); employee.setDepartmentId(10L); employee.setPositionId(20L); employee.setBranchId(30L);
        lenient().when(employees.findAllByTenantIdOrderByFirstNameAscLastNameAsc(7L)).thenReturn(List.of(employee));
        Timetable table = new Timetable(); table.setId(40L); table.setName("Səhər");
        lenient().when(timetables.findByTenantId(7L)).thenReturn(List.of(table));
        BiFunction<Employee, LocalDate, AttendanceScheduleResolver.DaySchedule> schedules = (e, d) ->
                new AttendanceScheduleResolver.DaySchedule(40L, "STANDARD", true, LocalTime.of(9, 0), LocalTime.of(18, 0), 0, 5, 0);
        lenient().when(scheduleResolver.forReport(eq(7L), anyList(), any(), any())).thenReturn(schedules);
        lenient().when(attendance.getEmployeeAttendanceForReport(eq(employee), any(), any(), any()))
                .thenReturn(List.of(day(start, 8, 25, 30, "EARLY_LEAVE"),
                        day(start.plusDays(1), 0, 10, 0, "MISSING_EXIT"), day(start.plusDays(2), 0, 0, 0, "ABSENT")));
        query = new AttendanceReportQuery(); query.setView(AttendanceReportQuery.View.DAILY);
        query.setStart(start); query.setEnd(start.plusDays(2));
    }
    @AfterEach void cleanup() { TenantContext.clear(); }

    @Test void missingExitFilterAndExcelIncludeOpenSessions() throws Exception {
        when(attendance.getEmployeeAttendanceForReport(eq(employee), any(), any(), any()))
                .thenReturn(List.of(day(start, 0, 10, 0, "OPEN_SESSION"),
                        day(start.plusDays(1), 0, 0, 0, "MISSING_EXIT"), day(start.plusDays(2), 8, 0, 0, "PRESENT")));
        query.setStatus("MISSING_EXIT");
        assertThat(service.report(query).getTotalElements()).isEqualTo(2);
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(service.export(query)))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(2);
            assertThat(sheet.getRow(1).toString()).doesNotContain("Açıq sessiya");
        }
        assertThat(AttendanceOverviewService.statusLabel("OPEN")).isEqualTo("Çıxış yoxdur");
        assertThat(AttendanceOverviewService.statusLabel("OPEN_SESSION")).isEqualTo("Çıxış yoxdur");
    }

    @Test void lateAndEarlyRemainIndependentlyVisibleAndFilterable() {
        query.setStatus("LATE");
        var late = service.report(query);
        assertThat(late.getTotalElements()).isEqualTo(2);
        assertThat(late.getContent().get(0).getStatuses()).contains("LATE", "EARLY_LEAVE");
        query.setStatus("EARLY_LEAVE");
        assertThat(service.report(query).getTotalElements()).isEqualTo(1);
        query.setStatus("LATE_AND_EARLY");
        assertThat(service.report(query).getTotalElements()).isEqualTo(1);
        query.setStatus("MISSING_EXIT");
        assertThat(service.report(query).getContent().get(0).getEarlyLeaveMinutes()).isZero();
    }

    @Test void monthlyAndYearlySumDaysOnceAndPaginationFollowsFiltering() {
        for (var view : List.of(AttendanceReportQuery.View.MONTHLY, AttendanceReportQuery.View.YEARLY)) {
            query.setView(view);
            var result = service.report(query);
            assertThat(result.getTotalElements()).isEqualTo(1);
            var row = result.getContent().get(0);
            assertThat(row.getWorkedMinutes()).isEqualTo(480);
            assertThat(row.getLateMinutes()).isEqualTo(35);
            assertThat(row.getEarlyLeaveMinutes()).isEqualTo(30);
            assertThat(row.getLateDays()).isEqualTo(2);
            assertThat(row.getAbsentDays()).isEqualTo(1);
            assertThat(row.getMissingExitDays()).isEqualTo(1);
            assertThat(row.getCheckInTime()).isNull();
        }
        query.setView(AttendanceReportQuery.View.DAILY); query.setStatus("LATE"); query.setSize(1); query.setPage(1);
        var page = service.report(query);
        assertThat(page.getTotalElements()).isEqualTo(2); assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getContent().get(0).getDate()).isEqualTo(start.plusDays(1));
    }

    @Test void exactIdFiltersAndAzerbaijaniNameSearchApplyBeforeCalculation() {
        query.setName("ilham"); query.setFin("123"); query.setDepartmentId(10L); query.setPositionId(20L); query.setTimetableId(40L);
        assertThat(service.report(query).getTotalElements()).isEqualTo(3);
        query.setTimetableId(41L); assertThat(service.report(query).getTotalElements()).isZero();
        query.setTimetableId(40L); query.setDepartmentId(11L);
        assertThat(service.report(query).getTotalElements()).isZero();
    }

    @Test void frozenVisitAreaWinsOverCurrentEmployeeMembership() {
        AttendanceLog log = new AttendanceLog(); log.setId(8L); log.setEmployeeId(1L);
        log.setCheckInTime(start.atTime(9, 0)); log.setAreaSnapshotCaptured(true); log.setAreaSnapshotId(99L); log.setAreaSnapshotName("Tarixi ərazi");
        when(logs.findByTenantIdAndCheckInTimeBetween(eq(7L), any(), any())).thenReturn(List.of(log));
        query.setAreaId(99L);
        assertThat(service.report(query).getContent()).singleElement().satisfies(row -> assertThat(row.getArea()).isEqualTo("Tarixi ərazi"));
        query.setAreaId(30L);
        assertThat(service.report(query).getContent()).extracting(AttendanceOverviewRowDTO::getDate).doesNotContain(start);
    }

    @Test void tenantAndBranchScopeNeverExposeOtherEmployees() {
        Employee foreign = new Employee(); foreign.setId(2L); foreign.setTenantId(8L);
        when(employees.findAllByTenantIdOrderByFirstNameAscLastNameAsc(7L)).thenReturn(List.of(employee, foreign));
        assertThat(service.report(query).getContent()).allMatch(row -> row.getEmployeePk().equals(1L));
        when(userScope.resolveBranchScope(null)).thenReturn(99L);
        assertThat(service.report(query).getTotalElements()).isZero();
        verify(attendance, never()).getEmployeeAttendanceForReport(eq(foreign), any(), any(), any());
    }

    @Test void exportUsesIdenticalStatusFiltersAndExportsAllPages() throws Exception {
        query.setStatus("LATE"); query.setSize(1); query.setPage(1);
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(service.export(query)))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(2);
            assertThat(sheet.getRow(1).getCell(11).getStringCellValue()).contains("Gecikib", "Erkən çıxıb");
            assertThat(sheet.getRow(1).getCell(12).getNumericCellValue()).isEqualTo(25);
        }
        verify(logs, never()).save(any());
        verify(attendance, never()).generateDailySummary(any(), any());
    }

    @Test void invalidRangesAndNonDailyStatusAreRejectedForBothFormats() {
        query.setEnd(start.minusDays(1));
        assertThatThrownBy(() -> service.report(query)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.export(query)).isInstanceOf(BadRequestException.class);
        query.setEnd(start.plusDays(366));
        assertThatThrownBy(() -> service.report(query)).isInstanceOf(BadRequestException.class);
        query.setEnd(start); query.setView(AttendanceReportQuery.View.YEARLY); query.setStatus("LATE");
        assertThatThrownBy(() -> service.export(query)).isInstanceOf(BadRequestException.class);
    }

    @Test void historicalTerminatedEmployeeKeepsVisitsWithoutInventedAbsences() {
        employee.setEmploymentStatus(Employee.EmploymentStatus.TERMINATED);
        assertThat(service.report(query).getContent()).hasSize(2);
    }

    @Test void hireDateAndTodayBoundTheCalculationWindow() {
        employee.setHireDate(start.plusDays(1)); query.setEnd(start.plusDays(3));
        service.report(query);
        verify(attendance).getEmployeeAttendanceForReport(eq(employee), eq(start.plusDays(1)), eq(start.plusDays(3)), any());
        query.setStart(com.hic.util.AppTimeZone.today().plusDays(1)); query.setEnd(query.getStart().plusDays(1));
        assertThat(service.report(query).getTotalElements()).isZero();
    }

    @Test void detailSearchUsesSameAzerbaijaniNameFilterAsDaily() {
        query.setView(AttendanceReportQuery.View.DETAILS); query.setName("ilham");
        var visit = new AttendanceReportRowDTO(); visit.setEmployeePk(1L); visit.setDate(start);
        when(details.getReport(start, query.getEnd(), null, null, null, null, null, null, null, 0, Integer.MAX_VALUE))
                .thenReturn(PaginatedResponse.of(List.of(visit), 1, 1, 0, Integer.MAX_VALUE));
        assertThat(service.report(query).getTotalElements()).isEqualTo(1);
        query.setName("another");
        assertThat(service.report(query).getTotalElements()).isZero();
    }

    private EmployeeAttendanceRowDTO day(LocalDate date, double hours, int late, int early, String status) {
        var row = new EmployeeAttendanceRowDTO(); row.setDate(date); row.setHoursWorked(hours);
        row.setLateMinutes(late); row.setEarlyLeaveMinutes(early);
        row.setStatus(DailyAttendanceSummary.AttendanceStatus.valueOf(status));
        if (!status.equals("ABSENT")) row.setCheckInTime(date.atTime(9, 30).atOffset(ZoneOffset.ofHours(4)));
        if (hours > 0) row.setCheckOutTime(date.atTime(17, 30).atOffset(ZoneOffset.ofHours(4)));
        return row;
    }
}
