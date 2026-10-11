package com.hic.service;

import com.hic.dto.AttendanceOverviewRowDTO;
import com.hic.dto.AttendanceReportQuery;
import com.hic.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class AttendanceReportSortTest {
    @Test void namesFollowAzerbaijaniAlphabetInBothDirections() {
        var names = List.of("Şəms", "Əli", "İlham", "Çinar", "Xəyal", "Səbinə", "Cavid", "Elvin", "Işıl");
        var rows = names.stream().map(name -> { var row = new AttendanceOverviewRowDTO(); row.setFullName(name); return row; }).toList();
        assertThat(sort(rows, "fullName", "asc")).extracting(AttendanceOverviewRowDTO::getFullName)
                .containsExactly("Cavid", "Çinar", "Elvin", "Əli", "Xəyal", "Işıl", "İlham", "Səbinə", "Şəms");
        assertThat(sort(rows, "fullName", "desc")).extracting(AttendanceOverviewRowDTO::getFullName)
                .containsExactly("Şəms", "Səbinə", "İlham", "Işıl", "Xəyal", "Əli", "Elvin", "Çinar", "Cavid");
    }

    @Test void numericDurationSortsByMinutesAndMissingValuesRemainLast() {
        var low = row(1L, 90); var high = row(2L, 720); var missing = row(3L, null);
        for (String field : List.of("workedMinutes", "overtimeMinutes")) {
            assertThat(sort(List.of(missing, low, high), field, "desc")).containsExactly(high, low, missing);
            assertThat(sort(List.of(missing, low, high), field, "asc")).containsExactly(low, high, missing);
        }
    }

    @Test void timestampsIncludeDateAndOvernightExitWithMissingExitsFollowingDirection() {
        var early = row(1L, 0); var late = row(2L, 0); var open = row(3L, 0);
        early.setCheckInTime(OffsetDateTime.parse("2026-10-09T20:00:00+04:00"));
        late.setCheckInTime(OffsetDateTime.parse("2026-10-10T01:00:00+04:00"));
        early.setCheckOutTime(OffsetDateTime.parse("2026-10-10T02:00:00+04:00"));
        late.setCheckOutTime(OffsetDateTime.parse("2026-10-10T05:00:00+04:00"));
        assertThat(sort(List.of(open, late, early), "checkInTime", "asc")).containsExactly(early, late, open);
        assertThat(sort(List.of(open, early, late), "checkInTime", "desc")).containsExactly(late, early, open);
        assertThat(sort(List.of(late, open, early), "checkOutTime", "asc")).containsExactly(early, late, open);
        assertThat(sort(List.of(early, open, late), "checkOutTime", "desc")).containsExactly(open, late, early);
    }

    @Test void idsUseNumericOrderForNumbersFinUsesTextAndStatusUsesVisibleLabels() {
        var two = row(1L, 0); two.setEmployeeId("2"); two.setFin("Z12"); two.setStatus("PRESENT");
        var ten = row(2L, 0); ten.setEmployeeId("10"); ten.setFin("A34"); ten.setStatus("MISSING_EXIT");
        var code = row(3L, 0); code.setEmployeeId("HR-3"); code.setFin(" "); code.setStatus("ABSENT");
        assertThat(sort(List.of(code, ten, two), "employeeId", "asc")).containsExactly(two, ten, code);
        assertThat(sort(List.of(two, code, ten), "employeeId", "desc")).containsExactly(code, ten, two);
        assertThat(sort(List.of(two, code, ten), "fin", "asc")).containsExactly(ten, two, code);
        assertThat(sort(List.of(ten, code, two), "fin", "desc")).containsExactly(two, ten, code);
        assertThat(sort(List.of(two, code, ten), "status", "asc")).containsExactly(ten, code, two);
        // Daily badges, rather than the raw enum, determine the displayed status text.
        two.setStatuses(List.of("EARLY_LEAVE", "LATE"));
        assertThat(sort(List.of(two, code, ten), "status", "asc")).containsExactly(ten, two, code);
    }

    @Test void equalValuesHaveStableOrderAndOmittedSortPreservesExistingOrder() {
        var first = row(1L, 60); var second = row(2L, 60);
        assertThat(sort(List.of(second, first), "workedMinutes", "desc")).containsExactly(first, second);
        assertThat(sort(List.of(second, first), null, "asc")).containsExactly(second, first);
    }

    @Test void invalidAndHiddenColumnsAreRejected() {
        var query = new AttendanceReportQuery(); query.setSortBy("password");
        assertThatThrownBy(() -> AttendanceReportSort.validate(query)).isInstanceOf(BadRequestException.class);
        query.setSortBy("fullName"); query.setSortDirection("invalid");
        assertThatThrownBy(() -> AttendanceReportSort.validate(query)).isInstanceOf(BadRequestException.class);
        query.setSortDirection("asc"); query.setSortBy("status"); query.setView(AttendanceReportQuery.View.MONTHLY);
        assertThatThrownBy(() -> AttendanceReportSort.validate(query)).isInstanceOf(BadRequestException.class);
    }

    private List<AttendanceOverviewRowDTO> sort(List<AttendanceOverviewRowDTO> rows, String field, String direction) {
        var query = new AttendanceReportQuery(); query.setSortBy(field); query.setSortDirection(direction);
        AttendanceReportSort.validate(query);
        return AttendanceReportSort.apply(rows, query);
    }

    private AttendanceOverviewRowDTO row(Long id, Integer minutes) {
        var row = new AttendanceOverviewRowDTO(); row.setEmployeePk(id);
        row.setWorkedMinutes(minutes); row.setOvertimeMinutes(minutes); return row;
    }
}
