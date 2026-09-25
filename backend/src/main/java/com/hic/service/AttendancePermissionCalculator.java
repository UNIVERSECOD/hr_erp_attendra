package com.hic.service;

import com.hic.model.DailyAttendanceSummary.AttendanceStatus;
import com.hic.model.EmployeePermission;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class AttendancePermissionCalculator {

    public Result apply(
            LocalDate date,
            AttendanceInferenceService.AttendanceInference inference,
            AttendanceScheduleResolver.DaySchedule schedule,
            AttendanceTimeCalculator.Calculation base,
            List<EmployeePermission> permissions
    ) {
        List<EmployeePermission> active = permissions == null
                ? List.of()
                : permissions.stream()
                .filter(permission -> appliesOn(permission, date))
                .toList();

        if (active.isEmpty()) {
            return Result.from(base);
        }

        TimeRange scheduleRange = scheduleRange(date, schedule);
        List<TimeRange> allRanges = merge(active.stream()
                .map(permission -> permissionRange(permission, date, scheduleRange))
                .filter(java.util.Objects::nonNull)
                .toList());
        List<TimeRange> creditableRanges = merge(active.stream()
                .filter(permission -> Boolean.FALSE.equals(permission.getDeductFromWorkHours()))
                .map(permission -> permissionRange(permission, date, scheduleRange))
                .filter(java.util.Objects::nonNull)
                .toList());
        List<TimeRange> explicitCreditableRanges = merge(active.stream()
                .filter(permission -> Boolean.FALSE.equals(permission.getDeductFromWorkHours()))
                .filter(permission -> !permission.isFullDay())
                .map(permission -> permissionRange(permission, date, scheduleRange))
                .filter(java.util.Objects::nonNull)
                .toList());

        int permissionMinutes = totalMinutes(allRanges);
        if (permissionMinutes == 0) {
            return Result.from(base);
        }

        boolean unresolvedSession = base.status() == AttendanceStatus.OPEN_SESSION
                || base.status() == AttendanceStatus.MISSING_EXIT;
        List<TimeRange> coveredRanges = attendanceRanges(inference, schedule);
        List<TimeRange> rangesForCredit = schedule.expectedMinutes() > 0
                ? creditableRanges
                : explicitCreditableRanges;
        int uncoveredCreditableMinutes = uncoveredMinutes(rangesForCredit, coveredRanges);
        int creditedMinutes = unresolvedSession ? 0 : uncoveredCreditableMinutes;
        if (schedule.expectedMinutes() > 0) {
            creditedMinutes = Math.min(
                    creditedMinutes,
                    Math.max(schedule.expectedMinutes() - base.workedMinutes(), 0)
            );
        }

        int workedMinutes = base.workedMinutes() + creditedMinutes;
        int overtimeMinutes = schedule.expectedMinutes() > 0
                ? Math.max(workedMinutes - schedule.expectedMinutes(), 0)
                : base.overtimeMinutes();

        int lateMinutes = adjustedLateMinutes(date, inference, schedule, base.lateMinutes(), allRanges);
        int earlyLeaveMinutes = adjustedEarlyLeaveMinutes(
                date, inference, schedule, base.earlyLeaveMinutes(), allRanges);
        boolean permittedEarlyLeave = base.earlyLeaveMinutes() > 0 && earlyLeaveMinutes == 0;
        boolean fullDayPermission = coversSchedule(active, allRanges, scheduleRange);

        AttendanceStatus status = resolveStatus(
                base.status(), inference, permittedEarlyLeave, fullDayPermission,
                lateMinutes, earlyLeaveMinutes
        );

        return new Result(
                status,
                workedMinutes,
                overtimeMinutes,
                lateMinutes,
                earlyLeaveMinutes,
                permissionMinutes,
                creditedMinutes,
                true
        );
    }

    private AttendanceStatus resolveStatus(
            AttendanceStatus baseStatus,
            AttendanceInferenceService.AttendanceInference inference,
            boolean permittedEarlyLeave,
            boolean fullDayPermission,
            int lateMinutes,
            int earlyLeaveMinutes
    ) {
        if (baseStatus == AttendanceStatus.OPEN_SESSION || baseStatus == AttendanceStatus.MISSING_EXIT) {
            return baseStatus;
        }
        if (permittedEarlyLeave) {
            return AttendanceStatus.PERMITTED_EARLY_LEAVE;
        }
        if (earlyLeaveMinutes > 0) {
            return AttendanceStatus.EARLY_LEAVE;
        }
        if (lateMinutes > 0) {
            return AttendanceStatus.LATE;
        }
        if (baseStatus == AttendanceStatus.ABSENT && !fullDayPermission) {
            return AttendanceStatus.ABSENT;
        }
        if (baseStatus == AttendanceStatus.DAY_OFF && inference.firstEntry() == null) {
            return AttendanceStatus.DAY_OFF;
        }
        return AttendanceStatus.ON_PERMISSION;
    }

    private int adjustedLateMinutes(
            LocalDate date,
            AttendanceInferenceService.AttendanceInference inference,
            AttendanceScheduleResolver.DaySchedule schedule,
            int baseLateMinutes,
            List<TimeRange> permissions
    ) {
        if (baseLateMinutes <= 0 || inference.firstEntry() == null || schedule.startTime() == null) {
            return baseLateMinutes;
        }
        LocalDateTime from = date.atTime(schedule.startTime()).plusMinutes(schedule.allowedLateMinutes());
        LocalDateTime to = inference.firstEntry();
        return Math.max(baseLateMinutes - overlapMinutes(permissions, new TimeRange(from, to)), 0);
    }

    private int adjustedEarlyLeaveMinutes(
            LocalDate date,
            AttendanceInferenceService.AttendanceInference inference,
            AttendanceScheduleResolver.DaySchedule schedule,
            int baseEarlyLeaveMinutes,
            List<TimeRange> permissions
    ) {
        if (baseEarlyLeaveMinutes <= 0
                || inference.lastExit() == null
                || schedule.startTime() == null
                || schedule.endTime() == null) {
            return baseEarlyLeaveMinutes;
        }
        LocalDateTime scheduledStart = date.atTime(schedule.startTime());
        LocalDateTime scheduledEnd = date.atTime(schedule.endTime());
        if (!scheduledEnd.isAfter(scheduledStart)) {
            scheduledEnd = scheduledEnd.plusDays(1);
        }
        LocalDateTime earliestNormalExit = scheduledEnd.minusMinutes(schedule.allowedEarlyLeaveMinutes());
        return Math.max(
                baseEarlyLeaveMinutes - overlapMinutes(
                        permissions,
                        new TimeRange(inference.lastExit(), earliestNormalExit)
                ),
                0
        );
    }

    private boolean appliesOn(EmployeePermission permission, LocalDate date) {
        if (permission == null || permission.getStartDate() == null || permission.getEndDate() == null) {
            return false;
        }
        EmployeePermission.Status status = permission.getStatus();
        boolean approved = status == EmployeePermission.Status.ACTIVE
                || status == EmployeePermission.Status.APPROVED;
        return approved
                && !date.isBefore(permission.getStartDate())
                && !date.isAfter(permission.getEndDate());
    }

    private TimeRange scheduleRange(LocalDate date, AttendanceScheduleResolver.DaySchedule schedule) {
        if (!schedule.workingDay() || schedule.startTime() == null || schedule.endTime() == null) {
            return null;
        }
        LocalDateTime start = date.atTime(schedule.startTime());
        LocalDateTime end = date.atTime(schedule.endTime());
        if (!end.isAfter(start)) {
            end = end.plusDays(1);
        }
        return new TimeRange(start, end);
    }

    private TimeRange permissionRange(EmployeePermission permission, LocalDate date, TimeRange scheduleRange) {
        TimeRange range;
        if (permission.isFullDay()) {
            range = scheduleRange != null
                    ? scheduleRange
                    : new TimeRange(date.atStartOfDay(), date.plusDays(1).atStartOfDay());
        } else if (permission.getStartTime() != null && permission.getEndTime() != null) {
            LocalDateTime start = date.atTime(permission.getStartTime());
            boolean overnightSchedule = scheduleRange != null
                    && scheduleRange.end().toLocalDate().isAfter(date);
            if (overnightSchedule && permission.getStartTime().isBefore(scheduleRange.start().toLocalTime())) {
                start = start.plusDays(1);
            }
            LocalDateTime end = start.toLocalDate().atTime(permission.getEndTime());
            if (!end.isAfter(start)) {
                end = end.plusDays(1);
            }
            range = new TimeRange(start, end);
        } else {
            return null;
        }
        return scheduleRange == null ? range : intersect(range, scheduleRange);
    }

    private boolean coversSchedule(
            List<EmployeePermission> permissions,
            List<TimeRange> ranges,
            TimeRange scheduleRange
    ) {
        if (permissions.stream().anyMatch(EmployeePermission::isFullDay)) {
            return true;
        }
        if (scheduleRange == null) {
            return false;
        }
        return overlapMinutes(ranges, scheduleRange) >= minutes(scheduleRange);
    }

    private List<TimeRange> attendanceRanges(
            AttendanceInferenceService.AttendanceInference inference,
            AttendanceScheduleResolver.DaySchedule schedule
    ) {
        if (!schedule.flexible()
                && !inference.currentlyInside()
                && inference.firstEntry() != null
                && inference.lastExit() != null
                && inference.lastExit().isAfter(inference.firstEntry())) {
            return List.of(new TimeRange(inference.firstEntry(), inference.lastExit()));
        }

        return merge(inference.segments().stream()
                .filter(segment -> segment.checkInTime() != null && segment.checkOutTime() != null)
                .filter(segment -> segment.checkOutTime().isAfter(segment.checkInTime()))
                .map(segment -> new TimeRange(segment.checkInTime(), segment.checkOutTime()))
                .toList());
    }

    private int uncoveredMinutes(List<TimeRange> permissions, List<TimeRange> covered) {
        int total = totalMinutes(permissions);
        int overlap = 0;
        for (TimeRange permission : permissions) {
            overlap += overlapMinutes(covered, permission);
        }
        return Math.max(total - overlap, 0);
    }

    private int overlapMinutes(List<TimeRange> ranges, TimeRange target) {
        int result = 0;
        for (TimeRange range : ranges) {
            TimeRange overlap = intersect(range, target);
            if (overlap != null) {
                result += minutes(overlap);
            }
        }
        return result;
    }

    private List<TimeRange> merge(List<TimeRange> ranges) {
        if (ranges.isEmpty()) {
            return List.of();
        }
        List<TimeRange> ordered = ranges.stream()
                .sorted(Comparator.comparing(TimeRange::start))
                .toList();
        List<TimeRange> merged = new ArrayList<>();
        TimeRange current = ordered.get(0);
        for (int index = 1; index < ordered.size(); index++) {
            TimeRange next = ordered.get(index);
            if (!next.start().isAfter(current.end())) {
                current = new TimeRange(
                        current.start(),
                        next.end().isAfter(current.end()) ? next.end() : current.end()
                );
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return List.copyOf(merged);
    }

    private TimeRange intersect(TimeRange left, TimeRange right) {
        LocalDateTime start = left.start().isAfter(right.start()) ? left.start() : right.start();
        LocalDateTime end = left.end().isBefore(right.end()) ? left.end() : right.end();
        return end.isAfter(start) ? new TimeRange(start, end) : null;
    }

    private int totalMinutes(List<TimeRange> ranges) {
        return ranges.stream().mapToInt(this::minutes).sum();
    }

    private int minutes(TimeRange range) {
        long value = Math.max(Duration.between(range.start(), range.end()).toMinutes(), 0);
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private record TimeRange(LocalDateTime start, LocalDateTime end) {
    }

    public record Result(
            AttendanceStatus status,
            int workedMinutes,
            int overtimeMinutes,
            int lateMinutes,
            int earlyLeaveMinutes,
            int permissionMinutes,
            int creditedPermissionMinutes,
            boolean hasPermission
    ) {
        static Result from(AttendanceTimeCalculator.Calculation calculation) {
            return new Result(
                    calculation.status(),
                    calculation.workedMinutes(),
                    calculation.overtimeMinutes(),
                    calculation.lateMinutes(),
                    calculation.earlyLeaveMinutes(),
                    0,
                    0,
                    false
            );
        }
    }
}
