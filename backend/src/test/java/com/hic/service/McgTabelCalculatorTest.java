package com.hic.service;

import com.hic.model.AttendanceLog;
import com.hic.model.EmployeePermission;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class McgTabelCalculatorTest {
    private final LocalDate date = LocalDate.of(2026, 9, 24);
    private final McgTabelCalculator calculator = new McgTabelCalculator(new AttendanceInferenceService(),
            new AttendanceTimeCalculator(), new AttendancePermissionCalculator());

    @ParameterizedTest
    @CsvSource({"09:00,15:00,0,360", "09:00,17:00,0,480", "08:00,20:00,0,720", "08:00,17:00,60,480", "20:00,02:00,0,360"})
    void completeDayUsesItsAssignedNormNotFixedEightHours(String start, String end, int rest, int minutes) {
        var schedule = schedule(start, end, rest, 5, 5);
        var result = calculate(schedule, log(start, end));
        assertThat(result.fullDay()).isTrue();
        assertThat(result.workedMinutes()).isEqualTo(minutes);
        assertThat(result.regularMinutes()).isEqualTo(minutes);
        assertThat(result.overtimeMinutes()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"09:05,16:55,true,470", "09:05:01,17:00,false,474", "09:00,16:54:59,false,474",
            "09:00:30,16:59:30,true,479", "08:00,16:00,false,480", "10:00,18:00,false,480"})
    void toleranceChecksBothBoundariesAndPreservesActualMinutes(String start, String end, boolean full, int minutes) {
        var result = calculate(schedule("09:00", "17:00", 0, 5, 5), log(start, end));
        assertThat(result.fullDay()).isEqualTo(full);
        assertThat(result.workedMinutes()).isEqualTo(minutes);
    }

    @Test
    void fullDayAndOvertimeAreSeparateUnits() {
        var result = calculate(standard(), log("09:00", "19:00"));
        assertThat(result.fullDay()).isTrue();
        assertThat(result.regularMinutes()).isEqualTo(480);
        assertThat(result.overtimeMinutes()).isEqualTo(120);
        assertThat(result.workedMinutes()).isEqualTo(600);
    }

    @Test
    void lateArrivalDoesNotBecomeFullDayThroughOvertime() {
        var result = calculate(standard(), log("10:00", "20:00"));
        assertThat(result.fullDay()).isFalse();
        assertThat(result.regularMinutes()).isEqualTo(480);
        assertThat(result.overtimeMinutes()).isEqualTo(120);
    }

    @ParameterizedTest
    @CsvSource({"false,true,480,120", "true,false,360,0"})
    void approvedEarlyPermissionCountsOnlyWhenNonDeductible(boolean deduct, boolean full, int minutes, int credit) {
        var result = calculate(standard(), log("09:00", "15:00"), permission("15:00", "17:00", deduct));
        assertThat(result.fullDay()).isEqualTo(full);
        assertThat(result.workedMinutes()).isEqualTo(minutes);
        assertThat(result.creditedPermissionMinutes()).isEqualTo(credit);
    }

    @Test
    void latePaidPermissionAndToleranceCanCompleteTheDayWithoutDoubleCounting() {
        var result = calculate(standard(), log("11:00", "16:55"), permission("09:00", "11:00", false));
        assertThat(result.fullDay()).isTrue();
        assertThat(result.workedMinutes()).isEqualTo(475);
        assertThat(result.creditedPermissionMinutes()).isEqualTo(120);
    }

    @Test
    void overlappingPaidPermissionsNeverCreateOvertime() {
        var result = calculate(standard(), log("09:00", "15:00"),
                permission("15:00", "17:00", false), permission("16:00", "17:00", false));
        assertThat(result.fullDay()).isTrue();
        assertThat(result.workedMinutes()).isEqualTo(480);
        assertThat(result.creditedPermissionMinutes()).isEqualTo(120);
        assertThat(result.overtimeMinutes()).isZero();
    }

    @Test
    void paidPermissionAndToleranceCannotHideAnUnpaidDeduction() {
        var result = calculate(standard(), log("09:00:30", "16:59:30"),
                permission("09:00", "09:01", false), permission("12:00", "12:01", true));
        assertThat(result.fullDay()).isFalse();
    }

    @Test
    void outsideShiftWorkDoesNotBuyBackDeductiblePermission() {
        var result = calculate(standard(), log("09:00", "20:00"), permission("12:00", "14:00", true));
        assertThat(result.fullDay()).isFalse();
        assertThat(result.workedMinutes()).isEqualTo(540);
        assertThat(result.overtimeMinutes()).isEqualTo(60);
    }

    @Test
    void paidWholeDayWithoutPunchesCountsAsDayButNeverAsOvertime() {
        var paid = permission(null, null, false);
        var result = calculator.calculate(date, List.of(), standard(), List.of(paid), ignored -> standard());
        assertThat(result.fullDay()).isTrue();
        assertThat(result.creditedPermissionMinutes()).isEqualTo(480);
        assertThat(result.overtimeMinutes()).isZero();
    }

    @Test
    void unapprovedPermissionCannotCompleteDay() {
        var permission = permission("15:00", "17:00", false);
        permission.setStatus(EmployeePermission.Status.REJECTED);
        assertThat(calculate(standard(), log("09:00", "15:00"), permission).fullDay()).isFalse();
    }

    @Test
    void missingExitCannotBeCompletedByPermissionOrEarlierClosedVisit() {
        var result = calculator.calculate(date, List.of(log("09:00", "17:00"), log("18:00", null)),
                standard(), List.of(permission(null, null, false)), ignored -> standard());
        assertThat(result.fullDay()).isFalse();
        assertThat(result.creditedPermissionMinutes()).isZero();
        assertThat(result.status()).isEqualTo("MISSING_EXIT");
    }

    @Test
    void flexibleAndRestDaysStayHours() {
        var flexible = new AttendanceScheduleResolver.DaySchedule(1L, "FLEXIBLE", true,
                LocalTime.of(9, 0), LocalTime.of(17, 0), 0, 5, 5);
        var result = calculate(flexible, log("09:00", "17:00"));
        assertThat(result.fullDay()).isFalse();
        assertThat(result.regularMinutes()).isEqualTo(480);
        assertThat(result.overtimeMinutes()).isZero();
        var rest = new AttendanceScheduleResolver.DaySchedule(1L, "STANDARD", false, null, null, 0, 0, 0);
        var off = calculate(rest, log("09:00", "11:00"));
        assertThat(off.fullDay()).isFalse();
        assertThat(off.overtimeMinutes()).isEqualTo(120);
    }

    @Test
    void defaultHoursCannotInventAssignmentButLegacyAssignedHoursCanCount() {
        var fallback = new AttendanceScheduleResolver.DaySchedule(null, "STANDARD", true,
                LocalTime.of(9, 0), LocalTime.of(17, 0), 0, 5, 5, false);
        var legacy = new AttendanceScheduleResolver.DaySchedule(null, "STANDARD", true,
                LocalTime.of(9, 0), LocalTime.of(17, 0), 0, 5, 5, true);
        assertThat(calculate(fallback, log("09:00", "17:00")).fullDay()).isFalse();
        assertThat(calculate(fallback, log("09:00", "19:00")).overtimeMinutes()).isZero();
        assertThat(calculate(fallback, log("09:00", "19:00")).regularMinutes()).isEqualTo(600);
        assertThat(calculate(legacy, log("09:00", "17:00")).fullDay()).isTrue();
    }

    @Test
    void overnightPermissionBelongsToShiftStartDate() {
        var schedule = schedule("20:00", "02:00", 0, 5, 5);
        var result = calculate(schedule, log("20:00", "01:00"), permission("01:00", "02:00", false));
        assertThat(result.fullDay()).isTrue();
        assertThat(result.workedMinutes()).isEqualTo(360);
    }

    private com.hic.dto.TabelMonthlyDTO.McgDayDTO calculate(AttendanceScheduleResolver.DaySchedule schedule,
            AttendanceLog log, EmployeePermission... permissions) {
        return calculator.calculate(date, List.of(log), schedule, List.of(permissions), ignored -> schedule);
    }

    private AttendanceScheduleResolver.DaySchedule standard() { return schedule("09:00", "17:00", 0, 5, 5); }

    private AttendanceScheduleResolver.DaySchedule schedule(String start, String end, int rest, int late, int early) {
        return new AttendanceScheduleResolver.DaySchedule(1L, "STANDARD", true,
                LocalTime.parse(start), LocalTime.parse(end), rest, late, early);
    }

    private AttendanceLog log(String start, String end) {
        var log = new AttendanceLog();
        log.setCheckInTime(date.atTime(LocalTime.parse(start)));
        if (end != null) {
            var exit = date.atTime(LocalTime.parse(end));
            if (!exit.isAfter(log.getCheckInTime())) exit = exit.plusDays(1);
            log.setCheckOutTime(exit);
        }
        return log;
    }

    private EmployeePermission permission(String start, String end, boolean deduct) {
        var permission = new EmployeePermission();
        permission.setStartDate(date); permission.setEndDate(date);
        permission.setStartTime(start == null ? null : LocalTime.parse(start));
        permission.setEndTime(end == null ? null : LocalTime.parse(end));
        permission.setStatus(EmployeePermission.Status.APPROVED);
        permission.setDeductFromWorkHours(deduct);
        return permission;
    }
}
