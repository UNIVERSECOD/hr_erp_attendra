package com.hic.service;

import com.hic.dto.TabelMonthlyDTO.McgDayDTO;
import com.hic.model.AttendanceLog;
import com.hic.model.EmployeePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;

/** Read-only MCG presentation. Never replaces recorded minutes with a day marker. */
@Service
@RequiredArgsConstructor
public class McgTabelCalculator {
    private final AttendanceInferenceService inferenceService;
    private final AttendanceTimeCalculator timeCalculator;
    private final AttendancePermissionCalculator permissionCalculator;

    public McgDayDTO calculate(LocalDate date, List<AttendanceLog> dayLogs,
            AttendanceScheduleResolver.DaySchedule schedule, List<EmployeePermission> permissions,
            Function<LocalDate, AttendanceScheduleResolver.DaySchedule> schedules) {
        var inference = inferenceService.inferDay(dayLogs, date);
        var result = permissionCalculator.applyAllocated(date, inference, schedule,
                timeCalculator.calculate(date, inference, schedule), permissions, schedules);
        int overtime = schedule.flexible() || !schedule.assigned() ? 0 : result.overtimeMinutes();
        boolean fullDay = false;
        // No guessed default schedule and no conversion of flexible/calendar-day portions into a day.
        if (schedule.assigned() && schedule.workingDay() && !schedule.flexible()
                && schedule.expectedMinutes() > 0 && !inference.currentlyInside()
                && result.lateMinutes() == 0 && result.earlyLeaveMinutes() == 0) {
            var start = date.atTime(schedule.startTime());
            var end = date.atTime(schedule.endTime());
            if (!end.isAfter(start)) end = end.plusDays(1);
            var withinShift = clipWithTolerance(inference, start, end, schedule);
            var regular = permissionCalculator.apply(date, withinShift, schedule,
                    timeCalculator.calculate(date, withinShift, schedule), permissions);
            // Outside-shift work cannot buy back a deductible permission or an incomplete shift.
            fullDay = regular.workedMinutes() >= schedule.expectedMinutes();
        }
        return new McgDayDTO(fullDay, result.workedMinutes(),
                Math.max(result.workedMinutes() - overtime, 0), overtime,
                result.permissionMinutes(), result.creditedPermissionMinutes(), result.status().name(), true);
    }

    private AttendanceInferenceService.AttendanceInference clipWithTolerance(
            AttendanceInferenceService.AttendanceInference inference, LocalDateTime start, LocalDateTime end,
            AttendanceScheduleResolver.DaySchedule schedule) {
        var first = inference.firstEntry();
        var last = inference.lastExit();
        if (first == null || last == null) {
            return new AttendanceInferenceService.AttendanceInference(null, null, 0, false, List.of());
        }
        // Only the completion check treats accepted boundary gaps as worked. Stored hours stay exact.
        // Recalculate permission overlap on this range so paid permission and tolerance never count twice.
        if (!first.isAfter(start.plusMinutes(schedule.allowedLateMinutes()))) first = start;
        if (!last.isBefore(end.minusMinutes(schedule.allowedEarlyLeaveMinutes()))) last = end;
        if (!last.isAfter(first) || !last.isAfter(start) || !first.isBefore(end)) {
            return new AttendanceInferenceService.AttendanceInference(null, null, 0, false, List.of());
        }
        int minutes = Math.toIntExact(Duration.between(first, last).toMinutes());
        return new AttendanceInferenceService.AttendanceInference(first, last, minutes, false,
                List.of(new AttendanceInferenceService.SessionSegment(first, last)));
    }
}
