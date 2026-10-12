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
    void extraWorkedTimeStaysHoursInsteadOfAFullDayMarker() {
        var result = calculate(standard(), log("09:00", "19:00"));
        assertThat(result.fullDay()).isFalse();
        assertThat(result.regularMinutes()).isEqualTo(480);
        assertThat(result.overtimeMinutes()).isEqualTo(120);
        assertThat(result.workedMinutes()).isEqualTo(600);
    }

    @ParameterizedTest
    @CsvSource({"17:01,481,0", "17:59,539,0", "17:59:59,539,0", "18:00,540,60", "18:01,541,61", "18:24,564,84"})
    void overtimeStartsAtOneHourAndThenCountsAllExtraMinutes(String exit, int worked, int overtime) {
        var result = calculate(standard(), log("09:00", exit));
        assertThat(result.fullDay()).isEqualTo(overtime == 0);
        assertThat(result.workedMinutes()).isEqualTo(worked);
        assertThat(result.overtimeMinutes()).isEqualTo(overtime);
        assertThat(result.expectedMinutes()).isEqualTo(480);
        assertThat(result.regularMinutes() + result.overtimeMinutes()).isEqualTo(worked);
    }

    @Test
    void completedEightToSeventeenShiftWithHalfHourExtraRemainsOne() {
        var result = calculate(schedule("08:00", "17:00", 0, 5, 5), log("08:00", "17:30"));
        assertThat(result.fullDay()).isTrue();
        assertThat(result.workedMinutes()).isEqualTo(570);
        assertThat(result.overtimeMinutes()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"16:30,true,510", "16:29:59,false,509", "16:20,false,500"})
    void earlyExitUsesConfiguredThirtyMinuteTolerance(String exit, boolean fullDay, int minutes) {
        var result = calculate(schedule("08:00", "17:00", 0, 5, 30), log("08:00", exit));
        assertThat(result.fullDay()).isEqualTo(fullDay);
        assertThat(result.workedMinutes()).isEqualTo(minutes);
        assertThat(result.overtimeMinutes()).isZero();
    }

    @Test
    void lessThanOneHourAfterEndCannotCompleteALateArrivalOutsideTolerance() {
        var result = calculate(schedule("08:00", "17:00", 0, 5, 5), log("08:15", "17:30"));
        assertThat(result.fullDay()).isFalse();
        assertThat(result.workedMinutes()).isEqualTo(555);
        assertThat(result.overtimeMinutes()).isZero();
    }

    @Test
    void lateArrivalDoesNotBecomeFullDayThroughOvertime() {
        var result = calculate(standard(), log("10:00", "20:00"));
        assertThat(result.fullDay()).isFalse();
        assertThat(result.regularMinutes()).isEqualTo(420);
        assertThat(result.overtimeMinutes()).isEqualTo(180);
    }

    @Test
    void earlyArrivalDoesNotCountTowardTheAfterShiftOvertimeGate() {
        var result = calculate(standard(), log("07:00", "17:59"));
        assertThat(result.workedMinutes()).isEqualTo(659);
        assertThat(result.overtimeMinutes()).isZero();
        assertThat(result.fullDay()).isTrue();
    }

    @Test
    void lateStartAndDeductedPermissionDoNotSubtractFromWorkAfterShiftEnd() {
        var result = calculate(schedule("09:00", "16:00", 0, 5, 5), log("13:21", "21:46"),
                permission("09:00", "13:30", true));
        assertThat(result.workedMinutes()).isEqualTo(496);
        assertThat(result.overtimeMinutes()).isEqualTo(346);
        assertThat(result.regularMinutes()).isEqualTo(150);
        assertThat(result.fullDay()).isFalse();
    }

    @Test
    void arrivalAfterShiftEndCountsOnlyTimeActuallyWorked() {
        var result = calculate(standard(), log("18:30", "19:00"));
        assertThat(result.workedMinutes()).isEqualTo(30);
        assertThat(result.overtimeMinutes()).isZero();
    }

    @Test
    void completedVisitsExcludeGapsFromOvertimeWithoutChangingExistingWorkedTime() {
        var schedule = schedule("09:00", "17:00", 0, 5, 5);
        var result = calculator.calculate(date,
                List.of(log("09:00", "17:30"), log("19:00", "19:45")),
                schedule, List.of(), ignored -> schedule);
        assertThat(result.overtimeMinutes()).isEqualTo(75);
        assertThat(result.workedMinutes()).isEqualTo(645);
        assertThat(result.regularMinutes()).isEqualTo(570);
        assertThat(result.fullDay()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"19:29,0", "19:30,60", "19:31,61"})
    void overtimeGateUsesSumOfClosedAfterEndVisitsNotElapsedTime(String exit, int overtime) {
        var result = calculator.calculate(date,
                List.of(log("09:00", "17:30"), log("19:00", exit)),
                standard(), List.of(), ignored -> standard());
        assertThat(result.overtimeMinutes()).isEqualTo(overtime);
        assertThat(result.fullDay()).isEqualTo(overtime == 0);
    }

    @Test
    void gapAcrossShiftEndDoesNotCountAsWorkAfterEnd() {
        var result = calculator.calculate(date,
                List.of(log("09:00", "16:30"), log("18:30", "19:00")),
                standard(), List.of(), ignored -> standard());
        assertThat(result.overtimeMinutes()).isZero();
        assertThat(result.workedMinutes()).isEqualTo(600);
    }

    @Test
    void overlappingAndNestedVisitsCountEachAfterEndMinuteOnlyOnce() {
        var result = calculator.calculate(date,
                List.of(log("18:20", "18:40"), log("09:00", "18:00"), log("17:30", "19:00")),
                standard(), List.of(), ignored -> standard());
        assertThat(result.overtimeMinutes()).isEqualTo(120);
        assertThat(result.workedMinutes()).isEqualTo(600);
    }

    @ParameterizedTest
    @CsvSource({"19:29:29,0", "19:29:30,60", "19:30:30,61"})
    void overtimeThresholdSumsSecondsAcrossVisitsBeforeConvertingToMinutes(String exit, int overtime) {
        var result = calculator.calculate(date,
                List.of(log("09:00", "17:30:30"), log("19:00", exit)),
                standard(), List.of(), ignored -> standard());
        assertThat(result.overtimeMinutes()).isEqualTo(overtime);
    }

    @Test
    void overtimeMatchesIndependentSecondBySecondPresenceAcrossMixedIntervals() {
        var random = new java.util.Random(20261013L);
        var end = date.atTime(17, 0);
        for (int scenario = 0; scenario < 100; scenario++) {
            var segments = new java.util.ArrayList<AttendanceInferenceService.SessionSegment>();
            var presence = new java.util.BitSet(10800);
            for (int visit = 0; visit < 8; visit++) {
                int entry = random.nextInt(9000) - 1800;
                int exit = Math.min(entry + random.nextInt(3600) + 1, 10800);
                boolean open = visit == 7;
                segments.add(new AttendanceInferenceService.SessionSegment(end.plusSeconds(entry),
                        open ? null : end.plusSeconds(exit)));
                if (!open && exit > 0) presence.set(Math.max(entry, 0), exit);
            }
            java.util.Collections.shuffle(segments, random);
            var inference = new AttendanceInferenceService.AttendanceInference(null, null, 0, true, segments);
            int actualMinutes = 30 + random.nextInt(180);
            int expected = Math.min(presence.cardinality() / 60, actualMinutes);
            assertThat(McgTabelCalculator.overtimeAfterShift(date, inference, standard(), actualMinutes))
                    .as("mixed visit scenario %s", scenario).isEqualTo(expected >= 60 ? expected : 0);
        }
    }

    @Test
    void overnightOvertimeStartsAtTheNextDaysScheduledEnd() {
        var result = calculate(schedule("20:00", "02:00", 0, 5, 5), log("20:00", "03:24"));
        assertThat(result.workedMinutes()).isEqualTo(444);
        assertThat(result.overtimeMinutes()).isEqualTo(84);
        assertThat(result.fullDay()).isFalse();
    }

    @Test
    void paidPermissionDoesNotHideActualOvertimeOrCreateAFullDayMarker() {
        var result = calculate(standard(), log("17:00", "18:00"), permission(null, null, false));
        assertThat(result.workedMinutes()).isEqualTo(480);
        assertThat(result.creditedPermissionMinutes()).isEqualTo(420);
        assertThat(result.overtimeMinutes()).isEqualTo(60);
        assertThat(result.fullDay()).isFalse();
    }

    @Test
    void missingExitCountsOnlyAlreadyClosedAfterShiftIntervals() {
        var result = calculator.calculate(date, List.of(log("09:00", "18:00"), log("19:00", null)),
                standard(), List.of(), ignored -> standard());
        assertThat(result.workedMinutes()).isEqualTo(540);
        assertThat(result.overtimeMinutes()).isEqualTo(60);
        assertThat(result.fullDay()).isFalse();
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
        assertThat(result.overtimeMinutes()).isEqualTo(180);
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
        assertThat(off.overtimeMinutes()).isZero();
        assertThat(off.scheduledDayOff()).isTrue();
        assertThat(off.expectedMinutes()).isZero();
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
