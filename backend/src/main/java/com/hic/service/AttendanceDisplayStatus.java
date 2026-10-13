package com.hic.service;

import com.hic.dto.EmployeeAttendanceRowDTO;
import com.hic.model.DailyAttendanceSummary.AttendanceStatus;
import com.hic.util.AppTimeZone;
import java.util.LinkedHashSet;
import java.util.List;

/** Read-only badges: completed work and a later missing exit can coexist. */
final class AttendanceDisplayStatus {
    private AttendanceDisplayStatus() {}

    static List<AttendanceStatus> forDay(EmployeeAttendanceRowDTO day,
            AttendanceInferenceService.AttendanceInference inference) {
        boolean hasSource = !inference.segments().isEmpty();
        boolean completed = hasSource ? inference.segments().stream().anyMatch(s ->
                s.checkInTime() != null && s.checkOutTime() != null && s.checkOutTime().isAfter(s.checkInTime()))
                : day.getCheckInTime() != null && day.getCheckOutTime() != null
                    && day.getCheckOutTime().isAfter(day.getCheckInTime());
        var primary = day.getStatus();
        boolean open = hasSource ? inference.currentlyInside()
                : primary == AttendanceStatus.OPEN_SESSION || primary == AttendanceStatus.MISSING_EXIT;
        if (primary == AttendanceStatus.OPEN_SESSION || primary == AttendanceStatus.MISSING_EXIT) {
            primary = completed || day.getCheckInTime() == null || !AppTimeZone.today().equals(day.getDate())
                    ? AttendanceStatus.MISSING_EXIT : AttendanceStatus.OPEN_SESSION;
        }
        var statuses = new LinkedHashSet<AttendanceStatus>();
        if (completed && primary != AttendanceStatus.WORKDAY_COMPLETE) statuses.add(AttendanceStatus.PRESENT);
        if (primary != null) statuses.add(primary);
        if (completed && open) statuses.add(AttendanceStatus.MISSING_EXIT);
        return List.copyOf(statuses);
    }
}
