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
