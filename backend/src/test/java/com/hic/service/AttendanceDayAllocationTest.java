package com.hic.service;

import com.hic.model.AttendanceLog;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class AttendanceDayAllocationTest {
    final LocalDate day = LocalDate.of(2026, 10, 31);
    final AttendanceInferenceService inference = new AttendanceInferenceService();

    @Test void flexibleMonthBoundarySplitsFourAndTwoWithoutChangingSource() {
        var log = session("FLEXIBLE");
        assertThat(minutes(log, day)).isEqualTo(240);
        assertThat(minutes(log, day.plusDays(1))).isEqualTo(120);
        assertThat(log.getCheckInTime()).isEqualTo(day.atTime(20, 0));
        assertThat(log.getCheckOutTime()).isEqualTo(day.plusDays(1).atTime(2, 0));
        assertThat(inference.inferDay(List.of(log), day).workedMinutes()).isEqualTo(360);
    }

    @Test void standardAndNightRemainOnEntryDate() {
        for (String shift : List.of("STANDARD", "NIGHT")) {
            var log = session(shift);
            assertThat(minutes(log, day)).isEqualTo(360);
            assertThat(minutes(log, day.plusDays(1))).isZero();
        }
    }

    @Test void unclosedFlexibleIntervalIsNotInventedAfterMidnight() {
        var log = session("FLEXIBLE"); log.setCheckOutTime(null);
        assertThat(minutes(log, day)).isZero();
        assertThat(AttendanceDayAllocation.forDay(List.of(log), day.plusDays(1), d -> "FLEXIBLE")).isEmpty();
    }

    @Test void exactMidnightHasNoExtraDayAndAliasesAreSupported() {
        var log = session("FIRST_ENTRY"); log.setCheckOutTime(day.plusDays(1).atStartOfDay());
        assertThat(AttendanceDayAllocation.affectedDates(log.getCheckInTime(), log.getCheckOutTime(), log.getShiftType()))
                .containsExactly(day);
        assertThat(minutes(log, day.plusDays(1))).isZero();
    }

    @Test void legacyRowsUseEntryDateAssignmentNotNextDaysSchedule() {
        var log = session(null);
        var portions = AttendanceDayAllocation.forDay(List.of(log), day.plusDays(1), d -> {
            assertThat(d).isEqualTo(day); return "FLEXIBLE";
        });
        assertThat(inference.inferDay(portions, day.plusDays(1)).workedMinutes()).isEqualTo(120);
    }

    @Test void multiDayManualCorrectionRecalculatesEveryAffectedDate() {
        var log = session("FLEXIBLE"); log.setManualOverride(true);
        log.setCheckOutTime(day.plusDays(2).atTime(2, 0));
        assertThat(AttendanceDayAllocation.affectedDates(log.getCheckInTime(), log.getCheckOutTime(), "FLEXIBLE"))
                .containsExactly(day, day.plusDays(1), day.plusDays(2));
        assertThat(minutes(log, day.plusDays(1))).isEqualTo(1440);
    }

    @Test void fractionalMinuteAcrossMidnightIsNotLost() {
        var log = session("FLEXIBLE");
        log.setCheckInTime(log.getCheckInTime().plusSeconds(30));
        log.setCheckOutTime(log.getCheckOutTime().plusSeconds(30));
        assertThat(minutes(log, day) + minutes(log, day.plusDays(1))).isEqualTo(360);
    }

    private int minutes(AttendanceLog log, LocalDate date) {
        return inference.inferDay(AttendanceDayAllocation.forDay(List.of(log), date, d -> "STANDARD"), date).workedMinutes();
    }
    private AttendanceLog session(String shift) {
        var log = new AttendanceLog(); log.setShiftType(shift);
        log.setCheckInTime(day.atTime(20, 0)); log.setCheckOutTime(day.plusDays(1).atTime(2, 0)); return log;
    }
}
