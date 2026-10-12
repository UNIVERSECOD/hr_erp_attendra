package com.hic.service;

import com.hic.model.AttendanceLog;
import com.hic.model.DailyAttendanceSummary;
import com.hic.model.Employee;
import com.hic.repository.AttendanceLogRepository;
import com.hic.repository.EmployeePermissionRepository;
import com.hic.util.AppTimeZone;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class McgTabelProjectionServiceTest {
    private final AttendanceLogRepository logs = mock(AttendanceLogRepository.class);
    private final EmployeePermissionRepository permissions = mock(EmployeePermissionRepository.class);
    private final AttendanceScheduleResolver schedules = mock(AttendanceScheduleResolver.class);
    private final McgTabelProjectionService service = new McgTabelProjectionService(logs, permissions, schedules,
            new McgTabelCalculator(new AttendanceInferenceService(), new AttendanceTimeCalculator(), new AttendancePermissionCalculator()));
    private final LocalDate date = LocalDate.of(2026, 9, 1);

    @Test
    void flexibleMonthCarryInIsSplitAndReplayDoesNotMutateSource() {
        var employee = employee();
        var source = new AttendanceLog(); source.setEmployeeId(employee.getId());
        source.setCheckInTime(date.minusDays(1).atTime(20, 0)); source.setCheckOutTime(date.atTime(2, 0));
        when(logs.findClosedCarryIn(7L, null, date.atStartOfDay())).thenReturn(List.of(source));
        var schedule = new AttendanceScheduleResolver.DaySchedule(1L, "FLEXIBLE", true,
                LocalTime.of(20, 0), LocalTime.of(2, 0), 0, 0, 0);
        when(schedules.forReport(eq(7L), anyList(), any(), any())).thenReturn((e, d) -> schedule);
        var projector = service.forMonth(7L, List.of(employee), date, date.plusDays(29));
        var result = projector.project(employee, date, null);
        assertThat(result.workedMinutes()).isEqualTo(120);
        assertThat(result.fullDay()).isFalse();
        assertThat(result.overtimeMinutes()).isZero();
        assertThat(projector.project(employee, date, null)).isEqualTo(result);
        assertThat(source.getCheckInTime()).isEqualTo(date.minusDays(1).atTime(20, 0));
        verify(logs).findByTenantIdAndCheckInTimeBetween(7L, date.atStartOfDay(), date.plusDays(29).atTime(LocalTime.MAX));
        verify(permissions).findByDateRange(7L, date.minusDays(1), date.plusDays(29));
        verify(logs, never()).save(any());
    }

    @Test
    void oldAggregateWithoutSourceRemainsHoursNotInventedFullDay() {
        var employee = employee();
        when(schedules.forReport(eq(7L), anyList(), any(), any())).thenReturn((e, d) -> standard());
        var summary = new DailyAttendanceSummary(); summary.setHoursWorked(8.0);
        var result = service.forMonth(7L, List.of(employee), date, date.plusDays(29)).project(employee, date, summary);
        assertThat(result.fullDay()).isFalse();
        assertThat(result.workedMinutes()).isEqualTo(480);
        assertThat(result.status()).isEqualTo("SUMMARY_ONLY");
        assertThat(result.sourceVerified()).isFalse();
    }

    @Test
    void futureDatesNeverCountEvenIfHistoricalSummaryWasSupplied() {
        var employee = employee();
        when(schedules.forReport(eq(7L), anyList(), any(), any())).thenReturn((e, d) -> standard());
        var future = AppTimeZone.today().plusMonths(1).withDayOfMonth(1);
        var summary = new DailyAttendanceSummary(); summary.setHoursWorked(8.0);
        var result = service.forMonth(7L, List.of(employee), future, future.plusDays(20)).project(employee, future, summary);
        assertThat(result.workedMinutes()).isZero();
        assertThat(result.status()).isEqualTo("FUTURE");
        assertThat(result.expectedMinutes()).isEqualTo(480);
        assertThat(result.scheduledDayOff()).isFalse();
    }

    @Test
    void futureRestDayKeepsScheduleFlagWithoutCountingArchivedHours() {
        var employee = employee();
        var rest = new AttendanceScheduleResolver.DaySchedule(1L, "STANDARD", false, null, null, 0, 0, 0);
        when(schedules.forReport(eq(7L), anyList(), any(), any())).thenReturn((e, d) -> rest);
        var future = AppTimeZone.today().plusMonths(1).withDayOfMonth(1);
        var summary = new DailyAttendanceSummary(); summary.setHoursWorked(8.0);
        var result = service.forMonth(7L, List.of(employee), future, future.plusDays(20)).project(employee, future, summary);
        assertThat(result.scheduledDayOff()).isTrue();
        assertThat(result.workedMinutes()).isZero();
        assertThat(result.expectedMinutes()).isZero();
        assertThat(result.fullDay()).isFalse();
    }

    @Test
    void partialPermissionWithoutRawLogsDoesNotReplaceKnownArchiveHours() {
        var employee = employee();
        var permission = new com.hic.model.EmployeePermission(); permission.setEmployeeId(employee.getId());
        permission.setStartDate(date); permission.setEndDate(date);
        permission.setStartTime(LocalTime.of(15, 0)); permission.setEndTime(LocalTime.of(17, 0));
        permission.setStatus(com.hic.model.EmployeePermission.Status.APPROVED);
        permission.setDeductFromWorkHours(false);
        when(permissions.findByDateRange(eq(7L), any(), any())).thenReturn(List.of(permission));
        when(schedules.forReport(eq(7L), anyList(), any(), any())).thenReturn((e, d) -> standard());
        var summary = new DailyAttendanceSummary(); summary.setHoursWorked(8.0);
        var result = service.forMonth(7L, List.of(employee), date, date.plusDays(29)).project(employee, date, summary);
        assertThat(result.workedMinutes()).isEqualTo(480);
        assertThat(result.sourceVerified()).isFalse();
        assertThat(result.fullDay()).isFalse();
    }

    @Test
    void emptySelectionDoesNotQueryAttendanceOrPermissions() {
        service.forMonth(7L, List.of(), date, date.plusDays(29));
        verifyNoInteractions(logs, permissions, schedules);
    }

    private AttendanceScheduleResolver.DaySchedule standard() {
        return new AttendanceScheduleResolver.DaySchedule(1L, "STANDARD", true,
                LocalTime.of(9, 0), LocalTime.of(17, 0), 0, 5, 5);
    }

    private Employee employee() {
        var employee = new Employee(); employee.setId(11L); employee.setTenantId(7L); return employee;
    }
}
