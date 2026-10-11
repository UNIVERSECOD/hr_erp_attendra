package com.hic.service;

import com.hic.model.Employee;
import com.hic.model.Timetable;
import com.hic.model.TimetableDayRule;
import com.hic.repository.TimetableDayRuleRepository;
import com.hic.repository.TimetableRepository;
import com.hic.repository.WorkScheduleRepository;
import com.hic.util.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceScheduleResolverTest {

    @Mock private EmployeeShiftResolver employeeShiftResolver;
    @Mock private TimetableRepository timetableRepository;
    @Mock private TimetableDayRuleRepository dayRuleRepository;
    @Mock private WorkScheduleRepository workScheduleRepository;

    @InjectMocks private AttendanceScheduleResolver resolver;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void permissionRequiresAssignedScheduleInsteadOfDefaultHours() {
        var employee = new Employee(); employee.setId(1L); employee.setTenantId(7L);
        var date = LocalDate.of(2026, 10, 12);
        when(employeeShiftResolver.resolve(employee, date))
                .thenReturn(new EmployeeShiftResolver.ResolvedShift(null, "STANDARD"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> resolver.resolveForPermission(employee, date))
                .isInstanceOf(com.hic.exception.BadRequestException.class);
        assertThat(resolver.resolve(employee, date).startTime()).isEqualTo(LocalTime.of(9, 0));
    }

    @Test
    void reportCacheUsesHistoricalAssignmentsAndWeeklyRulesWithoutPerDayQueries() {
        var assignmentRepository = org.mockito.Mockito.mock(com.hic.repository.EmployeeShiftAssignmentRepository.class);
        var realShiftResolver = new EmployeeShiftResolver(assignmentRepository, timetableRepository);
        var reportResolver = new AttendanceScheduleResolver(realShiftResolver, timetableRepository, dayRuleRepository, workScheduleRepository);
        Employee employee = new Employee(); employee.setId(1L); employee.setTenantId(7L); employee.setTimetableId(41L);
        Timetable old = new Timetable(); old.setId(40L); old.setShiftType("STANDARD"); old.setStartTime(LocalTime.of(9, 0)); old.setEndTime(LocalTime.of(18, 0));
        Timetable current = new Timetable(); current.setId(41L); current.setShiftType("FLEXIBLE");
        var assignment = new com.hic.model.EmployeeShiftAssignment(); assignment.setEmployeeId(1L); assignment.setTimetableId(40L);
        assignment.setEffectiveStartDate(LocalDate.of(2026, 1, 1)); assignment.setEffectiveEndDate(LocalDate.of(2026, 6, 30));
        var sunday = new TimetableDayRule(); sunday.setDayOfWeek(7); sunday.setWorkingDay(false);
        var start = LocalDate.of(2026, 1, 1); var end = LocalDate.of(2026, 12, 31);
        when(assignmentRepository.findActiveForEmployeesInDateRange(7L, java.util.Set.of(1L), start, end)).thenReturn(java.util.List.of(assignment));
        when(timetableRepository.findByTenantId(7L)).thenReturn(java.util.List.of(old, current));
        when(dayRuleRepository.findByTimetableIdOrderByDayOfWeek(40L)).thenReturn(java.util.List.of(sunday));
        var cache = reportResolver.forReport(7L, java.util.List.of(employee), start, end);
        assertThat(cache.apply(employee, LocalDate.of(2026, 6, 28)).workingDay()).isFalse();
        assertThat(cache.apply(employee, LocalDate.of(2026, 6, 29)).timetableId()).isEqualTo(40L);
        assertThat(cache.apply(employee, LocalDate.of(2026, 7, 1)).flexible()).isTrue();
        for (int i = 0; i < 365; i++) cache.apply(employee, start.plusDays(i));
        org.mockito.Mockito.verify(timetableRepository, org.mockito.Mockito.never()).findById(org.mockito.ArgumentMatchers.anyLong());
        org.mockito.Mockito.verify(dayRuleRepository, org.mockito.Mockito.never()).findByTimetableIdAndDayOfWeek(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void resolve_usesRuleForRequestedWeekdayWithinEmployeeTenant() {
        LocalDate saturday = LocalDate.of(2026, 9, 26);
        Employee employee = new Employee();
        employee.setId(10L);
        employee.setTenantId(7L);

        Timetable timetable = new Timetable();
        timetable.setId(20L);
        timetable.setTenantId(7L);

        TimetableDayRule saturdayRule = new TimetableDayRule();
        saturdayRule.setDayOfWeek(6);
        saturdayRule.setWorkingDay(true);
        saturdayRule.setStartTime(LocalTime.of(8, 0));
        saturdayRule.setEndTime(LocalTime.of(16, 0));
        saturdayRule.setBreakMinutes(30);
        saturdayRule.setAllowedLateMinutes(20);
        saturdayRule.setAllowedEarlyLeaveMinutes(15);

        when(employeeShiftResolver.resolve(employee, saturday))
                .thenReturn(new EmployeeShiftResolver.ResolvedShift(20L, "STANDARD"));
        when(timetableRepository.findByTenantIdAndId(7L, 20L)).thenReturn(Optional.of(timetable));
        when(dayRuleRepository.findByTimetableIdAndDayOfWeek(20L, 6))
                .thenReturn(Optional.of(saturdayRule));

        AttendanceScheduleResolver.DaySchedule result = resolver.resolve(employee, saturday);

        assertThat(result.workingDay()).isTrue();
        assertThat(result.startTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(result.endTime()).isEqualTo(LocalTime.of(16, 0));
        assertThat(result.breakMinutes()).isEqualTo(30);
        assertThat(result.allowedLateMinutes()).isEqualTo(20);
        assertThat(result.allowedEarlyLeaveMinutes()).isEqualTo(15);
        assertThat(result.expectedMinutes()).isEqualTo(450);
    }
}
