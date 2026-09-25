package com.hic.service;

import com.hic.model.DailyAttendanceSummary.AttendanceStatus;
import com.hic.model.EmployeePermission;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AttendancePermissionCalculatorTest {

    private final AttendanceTimeCalculator timeCalculator = new AttendanceTimeCalculator();
    private final AttendancePermissionCalculator permissionCalculator = new AttendancePermissionCalculator();
    private final LocalDate date = LocalDate.of(2026, 9, 24);

    @Test
    void approvedEarlyExit_notDeducted_completesExpectedHours() {
        var inference = inference(date.atTime(8, 0), date.atTime(15, 0));
        var schedule = standardSchedule();
        var base = timeCalculator.calculate(date, inference, schedule);

        var result = permissionCalculator.apply(
                date, inference, schedule, base,
                List.of(permission(LocalTime.of(15, 0), LocalTime.of(17, 0), false))
        );

        assertThat(result.status()).isEqualTo(AttendanceStatus.PERMITTED_EARLY_LEAVE);
        assertThat(result.workedMinutes()).isEqualTo(480);
        assertThat(result.permissionMinutes()).isEqualTo(120);
        assertThat(result.creditedPermissionMinutes()).isEqualTo(120);
        assertThat(result.earlyLeaveMinutes()).isZero();
    }

    @Test
    void approvedEarlyExit_deducted_keepsActualWorkedMinutes() {
        var inference = inference(date.atTime(8, 0), date.atTime(15, 0));
        var schedule = standardSchedule();
        var base = timeCalculator.calculate(date, inference, schedule);

        var result = permissionCalculator.apply(
                date, inference, schedule, base,
                List.of(permission(LocalTime.of(15, 0), LocalTime.of(17, 0), true))
        );

        assertThat(result.status()).isEqualTo(AttendanceStatus.PERMITTED_EARLY_LEAVE);
        assertThat(result.workedMinutes()).isEqualTo(360);
        assertThat(result.creditedPermissionMinutes()).isZero();
        assertThat(result.earlyLeaveMinutes()).isZero();
    }

    @Test
    void partialPermission_onlyExcusesCoveredEarlyLeaveMinutes() {
        var inference = inference(date.atTime(8, 0), date.atTime(15, 0));
        var schedule = standardSchedule();
        var base = timeCalculator.calculate(date, inference, schedule);

        var result = permissionCalculator.apply(
                date, inference, schedule, base,
                List.of(permission(LocalTime.of(16, 0), LocalTime.of(17, 0), false))
        );

        assertThat(result.status()).isEqualTo(AttendanceStatus.EARLY_LEAVE);
        assertThat(result.earlyLeaveMinutes()).isEqualTo(60);
        assertThat(result.workedMinutes()).isEqualTo(420);
    }

    @Test
    void overlappingPermissions_areNotCreditedTwice() {
        var inference = inference(date.atTime(8, 0), date.atTime(15, 0));
        var schedule = standardSchedule();
        var base = timeCalculator.calculate(date, inference, schedule);

        var result = permissionCalculator.apply(
                date, inference, schedule, base,
                List.of(
                        permission(LocalTime.of(15, 0), LocalTime.of(17, 0), false),
                        permission(LocalTime.of(16, 0), LocalTime.of(17, 0), false)
                )
        );

        assertThat(result.permissionMinutes()).isEqualTo(120);
        assertThat(result.creditedPermissionMinutes()).isEqualTo(120);
        assertThat(result.workedMinutes()).isEqualTo(480);
    }

    @Test
    void fullDayPermission_notDeducted_preservesFullScheduledDay() {
        var inference = new AttendanceInferenceService.AttendanceInference(null, null, 0, false, List.of());
        var schedule = standardSchedule();
        var base = timeCalculator.calculate(date, inference, schedule);
        EmployeePermission permission = permission(null, null, false);

        var result = permissionCalculator.apply(date, inference, schedule, base, List.of(permission));

        assertThat(result.status()).isEqualTo(AttendanceStatus.ON_PERMISSION);
        assertThat(result.workedMinutes()).isEqualTo(480);
        assertThat(result.creditedPermissionMinutes()).isEqualTo(480);
    }

    @Test
    void pendingPermission_doesNotAffectAttendance() {
        var inference = inference(date.atTime(8, 0), date.atTime(15, 0));
        var schedule = standardSchedule();
        var base = timeCalculator.calculate(date, inference, schedule);
        EmployeePermission permission = permission(LocalTime.of(15, 0), LocalTime.of(17, 0), false);
        permission.setStatus(EmployeePermission.Status.PENDING);

        var result = permissionCalculator.apply(date, inference, schedule, base, List.of(permission));

        assertThat(result.status()).isEqualTo(AttendanceStatus.EARLY_LEAVE);
        assertThat(result.permissionMinutes()).isZero();
        assertThat(result.workedMinutes()).isEqualTo(360);
    }

    @Test
    void flexibleShift_creditsOnlyExplicitPermissionDuration() {
        var inference = inference(date.atTime(8, 0), date.atTime(10, 0));
        var schedule = new AttendanceScheduleResolver.DaySchedule(
                3L, "FLEXIBLE", true,
                LocalTime.of(8, 0), LocalTime.of(17, 0),
                60, 0, 0
        );
        var base = timeCalculator.calculate(date, inference, schedule);

        var result = permissionCalculator.apply(
                date, inference, schedule, base,
                List.of(permission(LocalTime.of(10, 0), LocalTime.of(11, 0), false))
        );

        assertThat(result.workedMinutes()).isEqualTo(180);
        assertThat(result.creditedPermissionMinutes()).isEqualTo(60);
        assertThat(result.status()).isEqualTo(AttendanceStatus.ON_PERMISSION);
    }

    @Test
    void overnightSchedule_mapsAfterMidnightPermissionToSameWorkDate() {
        var inference = inference(date.atTime(20, 0), date.plusDays(1).atTime(2, 0));
        var schedule = new AttendanceScheduleResolver.DaySchedule(
                4L, "STANDARD", true,
                LocalTime.of(20, 0), LocalTime.of(4, 0),
                0, 0, 0
        );
        var base = timeCalculator.calculate(date, inference, schedule);

        var result = permissionCalculator.apply(
                date, inference, schedule, base,
                List.of(permission(LocalTime.of(2, 0), LocalTime.of(4, 0), false))
        );

        assertThat(result.status()).isEqualTo(AttendanceStatus.PERMITTED_EARLY_LEAVE);
        assertThat(result.permissionMinutes()).isEqualTo(120);
        assertThat(result.creditedPermissionMinutes()).isEqualTo(120);
        assertThat(result.workedMinutes()).isEqualTo(480);
    }

    @Test
    void openSession_statusTakesPriorityAndPermissionIsNotCreditedYet() {
        var inference = new AttendanceInferenceService.AttendanceInference(
                date.atTime(8, 0), null, 0, true,
                List.of(new AttendanceInferenceService.SessionSegment(date.atTime(8, 0), null))
        );
        var schedule = standardSchedule();
        var base = timeCalculator.calculate(date, inference, schedule);

        var result = permissionCalculator.apply(
                date, inference, schedule, base,
                List.of(permission(LocalTime.of(15, 0), LocalTime.of(17, 0), false))
        );

        assertThat(result.status()).isIn(AttendanceStatus.OPEN_SESSION, AttendanceStatus.MISSING_EXIT);
        assertThat(result.creditedPermissionMinutes()).isZero();
    }

    private AttendanceInferenceService.AttendanceInference inference(LocalDateTime start, LocalDateTime end) {
        int minutes = (int) java.time.Duration.between(start, end).toMinutes();
        return new AttendanceInferenceService.AttendanceInference(
                start, end, minutes, false,
                List.of(new AttendanceInferenceService.SessionSegment(start, end))
        );
    }

    private AttendanceScheduleResolver.DaySchedule standardSchedule() {
        return new AttendanceScheduleResolver.DaySchedule(
                2L, "STANDARD", true,
                LocalTime.of(8, 0), LocalTime.of(17, 0),
                60, 0, 0
        );
    }

    private EmployeePermission permission(LocalTime start, LocalTime end, boolean deduct) {
        EmployeePermission permission = new EmployeePermission();
        permission.setStartDate(date);
        permission.setEndDate(date);
        permission.setStartTime(start);
        permission.setEndTime(end);
        permission.setDeductFromWorkHours(deduct);
        permission.setStatus(EmployeePermission.Status.APPROVED);
        return permission;
    }
}
