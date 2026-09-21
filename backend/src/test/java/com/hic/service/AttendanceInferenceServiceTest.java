package com.hic.service;

import com.hic.model.AttendanceLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AttendanceInferenceServiceTest {

    private AttendanceInferenceService service;

    @BeforeEach
    void setUp() {
        service = new AttendanceInferenceService();
    }

    @Test
    void sameDaySession_countsFullyOnThatDay() {
        AttendanceLog log = session(
                LocalDateTime.of(2026, 4, 26, 9, 0),
                LocalDateTime.of(2026, 4, 26, 18, 0)
        );

        var day = service.inferDay(List.of(log), LocalDate.of(2026, 4, 26));

        assertThat(day.firstEntry()).isEqualTo(LocalDateTime.of(2026, 4, 26, 9, 0));
        assertThat(day.lastExit()).isEqualTo(LocalDateTime.of(2026, 4, 26, 18, 0));
        assertThat(day.workedMinutes()).isEqualTo(9 * 60);
    }

    @Test
    void midnightCrossing_splitsBetweenDays() {
        // Worked 22:00 day1 → 06:00 day2
        AttendanceLog log = session(
                LocalDateTime.of(2026, 4, 26, 22, 0),
                LocalDateTime.of(2026, 4, 27, 6, 0)
        );

        var day1 = service.inferDay(List.of(log), LocalDate.of(2026, 4, 26));
        var day2 = service.inferDay(List.of(log), LocalDate.of(2026, 4, 27));

        assertThat(day1.firstEntry()).isEqualTo(LocalDateTime.of(2026, 4, 26, 22, 0));
        assertThat(day1.lastExit()).isEqualTo(LocalDateTime.of(2026, 4, 27, 0, 0));
        assertThat(day1.workedMinutes()).isEqualTo(2 * 60);

        assertThat(day2.firstEntry()).isEqualTo(LocalDateTime.of(2026, 4, 27, 0, 0));
        assertThat(day2.lastExit()).isEqualTo(LocalDateTime.of(2026, 4, 27, 6, 0));
        assertThat(day2.workedMinutes()).isEqualTo(6 * 60);
    }

    @Test
    void overlapsDay_detectsCrossMidnightSession() {
        AttendanceLog log = session(
                LocalDateTime.of(2026, 4, 26, 22, 0),
                LocalDateTime.of(2026, 4, 27, 6, 0)
        );

        assertThat(service.overlapsDay(log, LocalDate.of(2026, 4, 26))).isTrue();
        assertThat(service.overlapsDay(log, LocalDate.of(2026, 4, 27))).isTrue();
        assertThat(service.overlapsDay(log, LocalDate.of(2026, 4, 25))).isFalse();
        assertThat(service.overlapsDay(log, LocalDate.of(2026, 4, 28))).isFalse();
    }

    @Test
    void multiSessionDay_standardUsesSpan_flexibleSumsIntervals() {
        AttendanceLog morning = session(
                LocalDateTime.of(2026, 4, 26, 9, 0),
                LocalDateTime.of(2026, 4, 26, 11, 0)
        );
        AttendanceLog midday = session(
                LocalDateTime.of(2026, 4, 26, 13, 0),
                LocalDateTime.of(2026, 4, 26, 15, 30)
        );
        AttendanceLog evening = session(
                LocalDateTime.of(2026, 4, 26, 17, 0),
                LocalDateTime.of(2026, 4, 26, 18, 0)
        );

        var day = service.inferDay(List.of(morning, midday, evening), LocalDate.of(2026, 4, 26));

        assertThat(day.intervalWorkedMinutes()).isEqualTo(330);
        assertThat(day.spanWorkedMinutes()).isEqualTo(9 * 60);
        assertThat(day.workedMinutesForShift("STANDARD")).isEqualTo(9 * 60);
        assertThat(day.workedMinutesForShift("NIGHT")).isEqualTo(9 * 60);
        assertThat(day.workedMinutesForShift("FLEXIBLE")).isEqualTo(330);
        assertThat(day.workedMinutesForShift("FIRST_ENTRY")).isEqualTo(330);
    }

    private static AttendanceLog session(LocalDateTime in, LocalDateTime out) {
        AttendanceLog log = new AttendanceLog();
        log.setEmployeeId(1L);
        log.setCheckInTime(in);
        log.setCheckOutTime(out);
        return log;
    }
}
