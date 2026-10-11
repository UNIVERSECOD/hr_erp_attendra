package com.hic.service;

import com.hic.dto.AttendanceOverviewRowDTO;
import com.hic.dto.AttendanceReportQuery;
import com.hic.exception.BadRequestException;

import java.math.BigInteger;
import java.text.Collator;
import java.text.RuleBasedCollator;
import java.text.ParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Sorting is applied to the complete filtered report, before pagination or Excel export. */
final class AttendanceReportSort {
    private static final Set<String> FIELDS = Set.of("fullName", "status", "checkInTime", "checkOutTime",
            "workedMinutes", "overtimeMinutes", "employeeId", "fin");
    private static final Set<String> VISIT_FIELDS = Set.of("status", "checkInTime", "checkOutTime");

    private AttendanceReportSort() {}

    static void validate(AttendanceReportQuery query) {
        boolean aggregate = query.getView() == AttendanceReportQuery.View.MONTHLY
                || query.getView() == AttendanceReportQuery.View.YEARLY;
        if (query.getSortBy() != null && (!FIELDS.contains(query.getSortBy())
                || aggregate && VISIT_FIELDS.contains(query.getSortBy()))
                || !Set.of("asc", "desc").contains(query.getSortDirection() == null ? "" : query.getSortDirection())) {
            throw new BadRequestException("Sıralama sütununu və istiqamətini düzgün seçin.");
        }
    }

    static List<AttendanceOverviewRowDTO> apply(List<AttendanceOverviewRowDTO> rows, AttendanceReportQuery query) {
        if (query.getSortBy() == null) return rows;
        boolean descending = "desc".equals(query.getSortDirection());
        Collator collator = azerbaijaniCollator(); // Collators are mutable; never share across requests.
        Comparator<String> text = collator::compare;
        Comparator<AttendanceOverviewRowDTO> order = switch (query.getSortBy()) {
            case "fullName" -> comparing(r -> nonBlank(r.getFullName()), text, descending);
            case "fin" -> comparing(r -> nonBlank(r.getFin()), text, descending);
            case "employeeId" -> comparing(r -> nonBlank(r.getEmployeeId()), (a, b) -> {
                boolean aNumber = a.matches("[0-9]+"); boolean bNumber = b.matches("[0-9]+");
                if (aNumber != bNumber) return aNumber ? -1 : 1;
                if (aNumber) return new BigInteger(a).compareTo(new BigInteger(b));
                return text.compare(a, b);
            }, descending);
            case "status" -> comparing(AttendanceReportSort::statusText, text, descending);
            case "checkInTime" -> comparing(AttendanceOverviewRowDTO::getCheckInTime, Comparator.naturalOrder(), descending);
            case "checkOutTime" -> comparing(AttendanceOverviewRowDTO::getCheckOutTime, Comparator.naturalOrder(), descending, descending);
            case "workedMinutes" -> comparing(AttendanceOverviewRowDTO::getWorkedMinutes, Comparator.naturalOrder(), descending);
            case "overtimeMinutes" -> comparing(AttendanceOverviewRowDTO::getOvertimeMinutes, Comparator.naturalOrder(), descending);
            default -> throw new IllegalArgumentException("Unvalidated report sort field");
        };
        // Deterministic ties keep records from shifting between pages. Missing exits come first
        // for latest-first ordering, last for earliest-first. Other missing values always stay last.
        order = order.thenComparing(AttendanceOverviewRowDTO::getDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(AttendanceOverviewRowDTO::getEmployeePk, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(AttendanceOverviewRowDTO::getCheckInTime, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(AttendanceOverviewRowDTO::getAttendanceLogId, Comparator.nullsLast(Comparator.naturalOrder()));
        return rows.stream().sorted(order).toList();
    }

    private static <T> Comparator<AttendanceOverviewRowDTO> comparing(Function<AttendanceOverviewRowDTO, T> field,
            Comparator<T> order, boolean descending) {
        return comparing(field, order, descending, false);
    }

    private static <T> Comparator<AttendanceOverviewRowDTO> comparing(Function<AttendanceOverviewRowDTO, T> field,
            Comparator<T> order, boolean descending, boolean missingFirst) {
        Comparator<T> directed = descending ? order.reversed() : order;
        return Comparator.comparing(field, missingFirst ? Comparator.nullsFirst(directed) : Comparator.nullsLast(directed));
    }

    private static String statusText(AttendanceOverviewRowDTO row) {
        if (row.getStatuses() != null && !row.getStatuses().isEmpty()) return row.getStatuses().stream()
                .map(AttendanceOverviewService::statusLabel).distinct().collect(Collectors.joining(", "));
        return row.getStatus() == null ? null : AttendanceOverviewService.statusLabel(row.getStatus());
    }

    private static String nonBlank(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private static Collator azerbaijaniCollator() {
        try {
            // Explicit alphabet also works on JDK distributions without Azerbaijani collation data.
            var collator = new RuleBasedCollator("< a,A < b,B < c,C < ç,Ç < d,D < e,E < ə,Ə < f,F < g,G < ğ,Ğ"
                    + " < h,H < x,X < ı,I < i,İ < j,J < k,K < q,Q < l,L < m,M < n,N < o,O < ö,Ö"
                    + " < p,P < r,R < s,S < ş,Ş < t,T < u,U < ü,Ü < v,V < y,Y < z,Z");
            collator.setStrength(Collator.PRIMARY);
            return collator;
        } catch (ParseException e) { throw new IllegalStateException("Invalid Azerbaijani collation rules", e); }
    }
}
