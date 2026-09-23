package com.hic.service;

import com.hic.model.DailyAttendanceSummary.AttendanceStatus;
import com.hic.util.AppTimeZone;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class AttendanceTimeCalculator {

    public Calculation calculate(
            LocalDate date,
            AttendanceInferenceService.AttendanceInference inference,
            AttendanceScheduleResolver.DaySchedule schedule
    ) {
        int rawWorkedMinutes = inference.workedMinutesForShift(schedule.shiftType());
        int workedMinutes = schedule.flexible()
                ? rawWorkedMinutes
                : Math.max(rawWorkedMinutes - (rawWorkedMinutes > 0 ? schedule.breakMinutes() : 0), 0);
        int overtimeMinutes = Math.max(workedMinutes - schedule.expectedMinutes(), 0);

        if (!schedule.workingDay()) {
            AttendanceStatus status = inference.firstEntry() == null && !inference.currentlyInside()
                    ? AttendanceStatus.DAY_OFF
                    : (inference.currentlyInside() || !date.equals(AppTimeZone.today())
                    ? AttendanceStatus.PRESENT
                    : AttendanceStatus.WORKDAY_COMPLETE);
            return new Calculation(status, workedMinutes, overtimeMinutes, 0, 0);
        }

        if (inference.firstEntry() == null && !inference.currentlyInside()) {
            return new Calculation(AttendanceStatus.ABSENT, 0, 0, 0, 0);
        }

        if (schedule.flexible() || schedule.startTime() == null || schedule.endTime() == null) {
            AttendanceStatus status = inference.currentlyInside()
                    ? AttendanceStatus.PRESENT
                    : (date.equals(AppTimeZone.today())
                    ? AttendanceStatus.WORKDAY_COMPLETE
                    : AttendanceStatus.PRESENT);
            return new Calculation(status, workedMinutes, overtimeMinutes, 0, 0);
        }

        LocalDateTime scheduledStart = date.atTime(schedule.startTime());
        LocalDateTime scheduledEnd = date.atTime(schedule.endTime());
        if (!scheduledEnd.isAfter(scheduledStart)) {
            scheduledEnd = scheduledEnd.plusDays(1);
        }

        LocalDateTime lateDeadline = scheduledStart.plusMinutes(schedule.allowedLateMinutes());
        int lateMinutes = inference.firstEntry() != null && inference.firstEntry().isAfter(lateDeadline)
                ? safeMinutes(Duration.between(lateDeadline, inference.firstEntry()))
                : 0;

        LocalDateTime earliestNormalExit = scheduledEnd.minusMinutes(schedule.allowedEarlyLeaveMinutes());
        int earlyLeaveMinutes = !inference.currentlyInside()
                && inference.lastExit() != null
                && inference.lastExit().isBefore(earliestNormalExit)
                ? safeMinutes(Duration.between(inference.lastExit(), earliestNormalExit))
                : 0;

        AttendanceStatus status;
        if (inference.currentlyInside() || inference.lastExit() == null) {
            status = lateMinutes > 0 ? AttendanceStatus.LATE : AttendanceStatus.PRESENT;
        } else if (earlyLeaveMinutes > 0) {
            status = AttendanceStatus.EARLY_LEAVE;
        } else if (lateMinutes > 0) {
            status = AttendanceStatus.LATE;
        } else {
            status = date.equals(AppTimeZone.today())
                    ? AttendanceStatus.WORKDAY_COMPLETE
                    : AttendanceStatus.PRESENT;
        }

        return new Calculation(status, workedMinutes, overtimeMinutes, lateMinutes, earlyLeaveMinutes);
    }

    private int safeMinutes(Duration duration) {
        long minutes = Math.max(duration.toMinutes(), 0);
        return minutes > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) minutes;
    }

    public record Calculation(
            AttendanceStatus status,
            int workedMinutes,
            int overtimeMinutes,
            int lateMinutes,
            int earlyLeaveMinutes
    ) {
    }
}
