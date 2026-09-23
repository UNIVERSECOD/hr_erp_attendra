package com.hic.service;

import com.hic.model.Employee;
import com.hic.model.Timetable;
import com.hic.model.TimetableDayRule;
import com.hic.model.WorkSchedule;
import com.hic.repository.TimetableDayRuleRepository;
import com.hic.repository.TimetableRepository;
import com.hic.repository.WorkScheduleRepository;
import com.hic.util.ShiftTypes;
import com.hic.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AttendanceScheduleResolver {

    private static final LocalTime DEFAULT_SHIFT_START = LocalTime.of(9, 0);
    private static final LocalTime DEFAULT_SHIFT_END = LocalTime.of(17, 0);
    private static final int DEFAULT_ALLOWED_LATE_MINUTES = 5;

    private final EmployeeShiftResolver employeeShiftResolver;
    private final TimetableRepository timetableRepository;
    private final TimetableDayRuleRepository dayRuleRepository;
    private final WorkScheduleRepository workScheduleRepository;

    public DaySchedule resolve(Employee employee, LocalDate date) {
        EmployeeShiftResolver.ResolvedShift resolvedShift = employeeShiftResolver.resolve(employee, date);
        if (resolvedShift.timetableId() != null) {
            Long tenantId = TenantContext.getTenantId() != null
                    ? TenantContext.getTenantId()
                    : employee != null ? employee.getTenantId() : null;
            Optional<Timetable> timetable = tenantId != null
                    ? timetableRepository.findByTenantIdAndId(tenantId, resolvedShift.timetableId())
                    : timetableRepository.findById(resolvedShift.timetableId());
            if (timetable.isPresent()) {
                return fromTimetable(timetable.get(), date, resolvedShift.shiftType());
            }
        }

        if (employee != null && employee.getId() != null) {
            Optional<WorkSchedule> legacySchedule = workScheduleRepository
                    .findTopByEmployeeIdAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(employee.getId(), date)
                    .filter(value -> value.getEndDate() == null || !value.getEndDate().isBefore(date));
            if (legacySchedule.isPresent()) {
                return fromLegacySchedule(legacySchedule.get(), date, resolvedShift.shiftType());
            }
        }

        return new DaySchedule(
                resolvedShift.timetableId(),
                resolvedShift.shiftType(),
                true,
                DEFAULT_SHIFT_START,
                DEFAULT_SHIFT_END,
                0,
                DEFAULT_ALLOWED_LATE_MINUTES,
                0
        );
    }

    private DaySchedule fromTimetable(Timetable timetable, LocalDate date, String resolvedShiftType) {
        String shiftType = resolvedShiftType != null ? resolvedShiftType : timetable.getShiftType();
        int isoDay = date.getDayOfWeek().getValue();
        Optional<TimetableDayRule> rule = dayRuleRepository.findByTimetableIdAndDayOfWeek(timetable.getId(), isoDay);
        if (rule.isPresent()) {
            TimetableDayRule value = rule.get();
            return new DaySchedule(
                    timetable.getId(),
                    shiftType,
                    Boolean.TRUE.equals(value.getWorkingDay()),
                    value.getStartTime(),
                    value.getEndTime(),
                    nonNegative(value.getBreakMinutes()),
                    nonNegative(value.getAllowedLateMinutes()),
                    nonNegative(value.getAllowedEarlyLeaveMinutes())
            );
        }

        return new DaySchedule(
                timetable.getId(),
                shiftType,
                true,
                timetable.getStartTime() != null ? timetable.getStartTime() : DEFAULT_SHIFT_START,
                timetable.getEndTime() != null ? timetable.getEndTime() : DEFAULT_SHIFT_END,
                nonNegative(timetable.getBreakMinutes()),
                nonNegative(timetable.getAllowedLateMinutes()),
                nonNegative(timetable.getAllowedEarlyLeaveMinutes())
        );
    }

    private DaySchedule fromLegacySchedule(WorkSchedule schedule, LocalDate date, String shiftType) {
        LocalTime start = getStart(schedule, date.getDayOfWeek());
        LocalTime end = getEnd(schedule, date.getDayOfWeek());
        boolean workingDay = start != null && end != null;
        return new DaySchedule(
                null,
                shiftType,
                workingDay,
                start,
                end,
                0,
                nonNegative(schedule.getGracePeriodMinutes()),
                0
        );
    }

    private LocalTime getStart(WorkSchedule schedule, DayOfWeek day) {
        return switch (day) {
            case MONDAY -> schedule.getMondayStart();
            case TUESDAY -> schedule.getTuesdayStart();
            case WEDNESDAY -> schedule.getWednesdayStart();
            case THURSDAY -> schedule.getThursdayStart();
            case FRIDAY -> schedule.getFridayStart();
            case SATURDAY -> schedule.getSaturdayStart();
            case SUNDAY -> schedule.getSundayStart();
        };
    }

    private LocalTime getEnd(WorkSchedule schedule, DayOfWeek day) {
        return switch (day) {
            case MONDAY -> schedule.getMondayEnd();
            case TUESDAY -> schedule.getTuesdayEnd();
            case WEDNESDAY -> schedule.getWednesdayEnd();
            case THURSDAY -> schedule.getThursdayEnd();
            case FRIDAY -> schedule.getFridayEnd();
            case SATURDAY -> schedule.getSaturdayEnd();
            case SUNDAY -> schedule.getSundayEnd();
        };
    }

    private int nonNegative(Integer value) {
        return value != null && value > 0 ? value : 0;
    }

    public record DaySchedule(
            Long timetableId,
            String shiftType,
            boolean workingDay,
            LocalTime startTime,
            LocalTime endTime,
            int breakMinutes,
            int allowedLateMinutes,
            int allowedEarlyLeaveMinutes
    ) {
        public boolean flexible() {
            return ShiftTypes.isFlexible(shiftType);
        }

        public int expectedMinutes() {
            if (!workingDay || flexible() || startTime == null || endTime == null) {
                return 0;
            }
            long span = Duration.between(startTime, endTime).toMinutes();
            if (span <= 0) {
                span += 24 * 60;
            }
            return Math.max((int) span - breakMinutes, 0);
        }
    }
}
