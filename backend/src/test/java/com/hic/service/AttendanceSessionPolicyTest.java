package com.hic.service;

import com.hic.model.AttendanceLog;
import com.hic.model.Employee;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceSessionPolicyTest {

    @Mock private AttendanceScheduleResolver scheduleResolver;

    @Test
    void flexibleOvernightSession_remainsOpenUntilMaximumDuration() {
        AttendanceSessionPolicy policy = new AttendanceSessionPolicy(scheduleResolver);
        Employee employee = new Employee();
        LocalDate day = LocalDate.of(2026, 9, 23);
        AttendanceLog session = openSession(day.atTime(20, 0));
        when(scheduleResolver.resolve(employee, day)).thenReturn(new AttendanceScheduleResolver.DaySchedule(
                1L, "FLEXIBLE", true, null, null, 0, 0, 0));

        assertThat(policy.isExpired(employee, session, day.plusDays(1).atTime(4, 0))).isFalse();
        assertThat(policy.isExpired(employee, session, day.plusDays(2).atTime(0, 0))).isTrue();
    }

    @Test
    void scheduledOvernightSession_usesNextDayEndAndGrace() {
        AttendanceSessionPolicy policy = new AttendanceSessionPolicy(scheduleResolver);
        Employee employee = new Employee();
        LocalDate day = LocalDate.of(2026, 9, 23);
        AttendanceLog session = openSession(day.atTime(20, 0));
        when(scheduleResolver.resolve(employee, day)).thenReturn(new AttendanceScheduleResolver.DaySchedule(
                1L, "NIGHT", true, LocalTime.of(20, 0), LocalTime.of(4, 0), 0, 0, 0));

        assertThat(policy.isExpired(employee, session, day.plusDays(1).atTime(9, 59))).isFalse();
        assertThat(policy.isExpired(employee, session, day.plusDays(1).atTime(10, 0))).isTrue();
    }

    @Test
    void flexibleSnapshot_isNotReclassifiedAfterScheduleChange() {
        AttendanceSessionPolicy policy = new AttendanceSessionPolicy(scheduleResolver);
        Employee employee = new Employee();
        LocalDate day = LocalDate.of(2026, 9, 23);
        AttendanceLog session = openSession(day.atTime(20, 0));
        session.setShiftType("FLEXIBLE");

        assertThat(policy.isExpired(employee, session, day.plusDays(1).atTime(4, 0))).isFalse();
        assertThat(policy.isExpired(employee, session, day.plusDays(1).atTime(20, 0))).isTrue();
    }

    @Test
    void lateScheduledEntry_getsMinimumSessionWindow() {
        AttendanceSessionPolicy policy = new AttendanceSessionPolicy(scheduleResolver);
        Employee employee = new Employee();
        LocalDate day = LocalDate.of(2026, 9, 23);
        AttendanceLog session = openSession(day.atTime(22, 0));
        when(scheduleResolver.resolve(employee, day)).thenReturn(new AttendanceScheduleResolver.DaySchedule(
                1L, "STANDARD", true, LocalTime.of(8, 0), LocalTime.of(17, 0), 0, 0, 0));

        assertThat(policy.isExpired(employee, session, day.atTime(23, 30))).isFalse();
        assertThat(policy.isExpired(employee, session, day.plusDays(1).atTime(6, 0))).isTrue();
    }

    private AttendanceLog openSession(LocalDateTime checkIn) {
        AttendanceLog session = new AttendanceLog();
        session.setCheckInTime(checkIn);
        return session;
    }
}
