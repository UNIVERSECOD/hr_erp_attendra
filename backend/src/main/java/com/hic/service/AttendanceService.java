package com.hic.service;

import com.hic.dto.AttendanceDTO;
import com.hic.dto.EmployeeAttendanceRowDTO;
import com.hic.dto.EmployeeAttendanceSummaryDTO;
import com.hic.dto.AttendanceLogDTO;
import com.hic.dto.AttendanceSessionDTO;
import com.hic.dto.DailyAttendanceSummaryDTO;
import com.hic.exception.ResourceNotFoundException;
import com.hic.model.AttendanceLog;
import com.hic.model.DailyAttendanceSummary;
import com.hic.model.DailyAttendanceSummary.AttendanceStatus;
import com.hic.model.Employee;
import com.hic.model.EmployeePermission;
import com.hic.model.LeaveRequest;
import com.hic.model.Timetable;
import com.hic.model.WorkSchedule;
import com.hic.repository.AttendanceLogRepository;
import com.hic.repository.DailyAttendanceSummaryRepository;
import com.hic.repository.EmployeePermissionRepository;
import com.hic.repository.EmployeeRepository;
import com.hic.repository.LeaveRequestRepository;
import com.hic.repository.TimetableRepository;
import com.hic.repository.WorkScheduleRepository;
import com.hic.util.AppTimeZone;
import com.hic.util.ShiftTypes;
import com.hic.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceLogRepository attendanceLogRepository;
    private final DailyAttendanceSummaryRepository summaryRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeePermissionRepository employeePermissionRepository;
    private final TimetableRepository timetableRepository;
    private final WorkScheduleRepository workScheduleRepository;
    private final UserScopeService userScopeService;
    private final AttendanceInferenceService attendanceInferenceService;
    private final EmployeeShiftResolver employeeShiftResolver;

    private static final LocalTime DEFAULT_SHIFT_START = LocalTime.of(9, 0);
    private static final LocalTime DEFAULT_SHIFT_END = LocalTime.of(17, 0);
    private static final int DEFAULT_ALLOWED_LATE_MINUTES = 5;

    @Transactional
    public AttendanceLogDTO logAttendance(AttendanceDTO dto) {
        AttendanceLog log = new AttendanceLog();
        log.setEmployeeId(dto.getEmployeeId());
        log.setCheckInTime(dto.getCheckInTime());
        log.setCheckOutTime(dto.getCheckOutTime());
        log.setDeviceId(dto.getDeviceId());
        log.setDoorId(dto.getDoorId());
        log.setEventType(dto.getEventType());
        log.setVerificationMethod(dto.getVerificationMethod());
        log.setStatus("ACTIVE");
        Long tenantId = TenantContext.getTenantId();
        if (tenantId != null) {
            log.setTenantId(tenantId);
        }
        AttendanceLog saved = attendanceLogRepository.save(log);
        if (dto.getCheckInTime() != null) {
            generateDailySummary(dto.getEmployeeId(), dto.getCheckInTime().toLocalDate());
        }
        return toLogDTO(saved);
    }

    public List<AttendanceLogDTO> getLogsForEmployee(Long employeeId, LocalDateTime start, LocalDateTime end) {
        Long tenantId = TenantContext.getTenantId();
        List<AttendanceLog> logs = tenantId != null
                ? attendanceLogRepository.findByTenantIdAndEmployeeIdAndCheckInTimeBetween(tenantId, employeeId, start, end)
                : attendanceLogRepository.findByEmployeeIdAndCheckInTimeBetween(employeeId, start, end);
        return logs.stream().map(this::toLogDTO).collect(Collectors.toList());
    }

    public List<AttendanceLogDTO> getLogsByDateRange(LocalDateTime start, LocalDateTime end) {
        Long tenantId = TenantContext.getTenantId();
        List<AttendanceLog> logs = tenantId != null
                ? attendanceLogRepository.findByTenantIdAndCheckInTimeBetween(tenantId, start, end)
                : attendanceLogRepository.findByCheckInTimeBetween(start, end);
        return logs.stream().map(this::toLogDTO).collect(Collectors.toList());
    }

    @Transactional
    public DailyAttendanceSummaryDTO generateDailySummary(Long employeeId, LocalDate date) {
        List<AttendanceLog> logs = findDayLogs(employeeId, date);
        AttendanceInferenceService.AttendanceInference inference = attendanceInferenceService.inferDay(logs, date);
        Employee employee = employeeRepository.findById(employeeId).orElse(null);
        String shiftType = resolveShiftType(employee, date);
        ScheduleSettings scheduleSettings = resolveScheduleSettings(employee, date);

        Optional<DailyAttendanceSummary> existing = summaryRepository.findByEmployeeIdAndAttendanceDate(employeeId, date);
        DailyAttendanceSummary summary = existing.orElse(new DailyAttendanceSummary());
        summary.setTenantId(resolveAttendanceTenantId(employeeId));
        summary.setEmployeeId(employeeId);
        summary.setAttendanceDate(date);
        summary.setIsHoliday(false);
        summary.setIsLeave(false);
        summary.setIsStandardDay(true);
        summary.setIsAdditionalDay(false);
        summary.setIsExtraDay(false);

        if (inference.firstEntry() == null && !inference.currentlyInside()) {
            summary.setCheckInTime(null);
            summary.setCheckOutTime(null);
            summary.setAttendanceStatus(AttendanceStatus.ABSENT);
            summary.setHoursWorked(0.0);
        } else {
            summary.setCheckInTime(inference.firstEntry());
            summary.setCheckOutTime(inference.lastExit());
            summary.setHoursWorked(inference.workedHoursForShift(shiftType));
            summary.setAttendanceStatus(determineStatus(date, inference, scheduleSettings, shiftType));
        }

        return toSummaryDTO(summaryRepository.save(summary));
    }

    public List<DailyAttendanceSummaryDTO> getDailySummary(Long employeeId, LocalDate start, LocalDate end) {
        return summaryRepository.findByEmployeeIdAndAttendanceDateBetween(employeeId, start, end)
                .stream().map(this::toSummaryDTO).collect(Collectors.toList());
    }

    public List<EmployeeAttendanceRowDTO> getEmployeeAttendance(Long employeeId, LocalDate start, LocalDate end) {
        Employee employee = getAccessibleEmployee(employeeId);
        Long tenantId = TenantContext.getTenantId();

        // Include previous-day sessions that may cross midnight into `start`.
        LocalDateTime rangeStart = start.minusDays(1).atStartOfDay();
        LocalDateTime rangeEnd = end.atTime(23, 59, 59);
        List<AttendanceLog> logs = tenantId != null
                ? attendanceLogRepository.findByTenantIdAndEmployeeIdAndCheckInTimeBetween(tenantId, employeeId, rangeStart, rangeEnd)
                : attendanceLogRepository.findByEmployeeIdAndCheckInTimeBetween(employeeId, rangeStart, rangeEnd);

        Map<LocalDate, DailyAttendanceSummary> summariesByDate = summaryRepository
                .findByEmployeeIdAndAttendanceDateBetween(employeeId, start, end)
                .stream()
                .collect(Collectors.toMap(DailyAttendanceSummary::getAttendanceDate, summary -> summary, (left, right) -> right, LinkedHashMap::new));

        List<LeaveRequest> approvedLeaves = tenantId != null
                ? leaveRequestRepository.findApprovedByTenantAndEmployeeIdAndDateRange(tenantId, employeeId, start, end)
                : leaveRequestRepository.findApprovedByEmployeeIdAndDateRange(employeeId, start, end);
        List<EmployeePermission> approvedPermissions = tenantId != null
                ? employeePermissionRepository.findByTenantIdAndEmployeeIdAndDateRange(tenantId, employeeId, start, end)
                : employeePermissionRepository.findByEmployeeIdAndDateRange(employeeId, start, end);
        Optional<Timetable> timetable = getEmployeeTimetable(employee, tenantId);

        List<EmployeeAttendanceRowDTO> rows = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            final LocalDate currentDate = date;
            DailyAttendanceSummary summary = summariesByDate.get(currentDate);
            List<AttendanceLog> dayLogs = logs.stream()
                    .filter(log -> attendanceInferenceService.overlapsDay(log, currentDate))
                    .toList();
            boolean onLeave = overlapsLeave(approvedLeaves, currentDate) || overlapsPermission(approvedPermissions, currentDate);
            AttendanceInferenceService.AttendanceInference inference = attendanceInferenceService.inferDay(dayLogs, currentDate);

            // Prefer live day-clipped inference for hours/times so midnight splits stay correct.
            // Shift type is resolved as-of this calendar day so schedule changes do not rewrite history.
            String shiftType = resolveShiftType(employee, currentDate);
            Optional<Timetable> dayTimetable = resolveTimetableForDate(employee, currentDate, timetable);
            LocalDateTime firstCheckIn = inference.firstEntry();
            LocalDateTime lastCheckOut = inference.lastExit();
            double hoursWorked = inference.workedHoursForShift(shiftType);

            if (summary != null && summary.getHoursWorked() != null && dayLogs.isEmpty()) {
                hoursWorked = summary.getHoursWorked();
                firstCheckIn = summary.getCheckInTime();
                lastCheckOut = summary.getCheckOutTime();
            }

            EmployeeAttendanceRowDTO row = new EmployeeAttendanceRowDTO();
            row.setDate(currentDate);
            row.setCheckInTime(toOffsetDateTime(firstCheckIn));
            row.setCheckOutTime(toOffsetDateTime(lastCheckOut));
            row.setHoursWorked(hoursWorked);
            row.setStatus(determineDailyStatus(summary, inference, onLeave, currentDate, dayTimetable.orElse(null), shiftType));
            row.setNotes(buildNotes(approvedLeaves, approvedPermissions, currentDate));
            row.setShiftType(shiftType);
            row.setSessions(toSessionDtos(inference.segments()));
            rows.add(row);
        }

        return rows;
    }

    public EmployeeAttendanceSummaryDTO getEmployeeAttendanceSummary(Long employeeId, LocalDate start, LocalDate end) {
        List<EmployeeAttendanceRowDTO> rows = getEmployeeAttendance(employeeId, start, end);
        EmployeeAttendanceSummaryDTO summary = new EmployeeAttendanceSummaryDTO();
        summary.setTotalDays(rows.size());
        summary.setWorkingDays(rows.stream()
                .filter(row -> row.getStatus() == AttendanceStatus.PRESENT
                        || row.getStatus() == AttendanceStatus.LATE
                        || row.getStatus() == AttendanceStatus.WORKDAY_COMPLETE)
                .count());
        summary.setTotalHours(rows.stream()
                .map(EmployeeAttendanceRowDTO::getHoursWorked)
                .filter(java.util.Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum());
        summary.setAbsentDays(rows.stream().filter(row -> row.getStatus() == AttendanceStatus.ABSENT).count());
        summary.setLateDays(rows.stream().filter(row -> row.getStatus() == AttendanceStatus.LATE).count());
        summary.setLeaveDays(rows.stream().filter(row -> row.getStatus() == AttendanceStatus.ON_LEAVE).count());
        return summary;
    }

    private java.time.LocalTime getScheduleStart(WorkSchedule s, java.time.DayOfWeek day) {
        return switch (day) {
            case MONDAY -> s.getMondayStart();
            case TUESDAY -> s.getTuesdayStart();
            case WEDNESDAY -> s.getWednesdayStart();
            case THURSDAY -> s.getThursdayStart();
            case FRIDAY -> s.getFridayStart();
            case SATURDAY -> s.getSaturdayStart();
            case SUNDAY -> s.getSundayStart();
        };
    }

    private java.time.LocalTime getScheduleEnd(WorkSchedule s, java.time.DayOfWeek day) {
        return switch (day) {
            case MONDAY -> s.getMondayEnd();
            case TUESDAY -> s.getTuesdayEnd();
            case WEDNESDAY -> s.getWednesdayEnd();
            case THURSDAY -> s.getThursdayEnd();
            case FRIDAY -> s.getFridayEnd();
            case SATURDAY -> s.getSaturdayEnd();
            case SUNDAY -> s.getSundayEnd();
        };
    }

    private AttendanceLogDTO toLogDTO(AttendanceLog log) {
        AttendanceLogDTO dto = new AttendanceLogDTO();
        dto.setId(log.getId());
        dto.setEmployeeId(log.getEmployeeId());
        dto.setCheckInTime(toOffsetDateTime(log.getCheckInTime()));
        dto.setCheckOutTime(toOffsetDateTime(log.getCheckOutTime()));
        dto.setDeviceId(log.getDeviceId());
        dto.setDoorId(log.getDoorId());
        dto.setEventType(log.getEventType());
        dto.setVerificationMethod(log.getVerificationMethod());
        dto.setStatus(log.getStatus());
        dto.setCreatedAt(toOffsetDateTime(log.getCreatedAt()));
        return dto;
    }

    private DailyAttendanceSummaryDTO toSummaryDTO(DailyAttendanceSummary s) {
        DailyAttendanceSummaryDTO dto = new DailyAttendanceSummaryDTO();
        dto.setId(s.getId());
        dto.setEmployeeId(s.getEmployeeId());
        dto.setAttendanceDate(s.getAttendanceDate());
        dto.setCheckInTime(toOffsetDateTime(s.getCheckInTime()));
        dto.setCheckOutTime(toOffsetDateTime(s.getCheckOutTime()));
        dto.setHoursWorked(s.getHoursWorked());
        dto.setIsStandardDay(s.getIsStandardDay());
        dto.setIsAdditionalDay(s.getIsAdditionalDay());
        dto.setIsExtraDay(s.getIsExtraDay());
        dto.setIsHoliday(s.getIsHoliday());
        dto.setIsLeave(s.getIsLeave());
        dto.setAttendanceStatus(s.getAttendanceStatus());
        return dto;
    }

    private Employee getAccessibleEmployee(Long employeeId) {
        Long tenantId = TenantContext.getTenantId();
        Employee employee = tenantId != null
                ? employeeRepository.findByTenantIdAndId(tenantId, employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", employeeId))
                : employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", employeeId));

        Long branchScope = userScopeService.resolveBranchScope(null);
        if (branchScope != null && (employee.getBranchId() == null || !branchScope.equals(employee.getBranchId()))) {
            throw new ResourceNotFoundException("Employee", employeeId);
        }
        return employee;
    }

    private Long resolveAttendanceTenantId(Long employeeId) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId != null) {
            return tenantId;
        }
        return employeeRepository.findById(employeeId)
                .map(Employee::getTenantId)
                .orElse(null);
    }

    private Optional<Timetable> getEmployeeTimetable(Employee employee, Long tenantId) {
        if (employee.getTimetableId() == null) {
            return Optional.empty();
        }
        return tenantId != null
                ? timetableRepository.findByTenantIdAndId(tenantId, employee.getTimetableId())
                : timetableRepository.findById(employee.getTimetableId());
    }

    private boolean overlapsLeave(List<LeaveRequest> leaves, LocalDate date) {
        return leaves.stream().anyMatch(leave ->
                !leave.getStartDate().isAfter(date) && !leave.getEndDate().isBefore(date));
    }

    private boolean overlapsPermission(List<EmployeePermission> permissions, LocalDate date) {
        return permissions.stream().anyMatch(permission ->
                !permission.getStartDate().isAfter(date) && !permission.getEndDate().isBefore(date));
    }

    private AttendanceStatus determineDailyStatus(DailyAttendanceSummary summary,
                                                  AttendanceInferenceService.AttendanceInference inference,
                                                  boolean onLeave,
                                                  LocalDate date,
                                                  Timetable timetable,
                                                  String shiftType) {
        if (onLeave) {
            return AttendanceStatus.ON_LEAVE;
        }
        if (summary != null && summary.getAttendanceStatus() == AttendanceStatus.ON_LEAVE) {
            return AttendanceStatus.ON_LEAVE;
        }
        if (summary != null && summary.getAttendanceStatus() != null) {
            return summary.getAttendanceStatus();
        }

        ScheduleSettings scheduleSettings = timetable != null
                ? new ScheduleSettings(
                timetable.getStartTime() != null ? timetable.getStartTime() : DEFAULT_SHIFT_START,
                timetable.getEndTime() != null ? timetable.getEndTime() : DEFAULT_SHIFT_END,
                timetable.getAllowedLateMinutes() != null ? timetable.getAllowedLateMinutes() : DEFAULT_ALLOWED_LATE_MINUTES
        )
                : resolveScheduleSettings(null, date);
        return determineStatus(date, inference, scheduleSettings, shiftType);
    }

    private String resolveShiftType(Employee employee) {
        return resolveShiftType(employee, LocalDate.now());
    }

    private String resolveShiftType(Employee employee, LocalDate date) {
        if (employee == null) {
            return null;
        }
        EmployeeShiftResolver.ResolvedShift resolved = employeeShiftResolver.resolve(employee, date);
        if (resolved.shiftType() != null && !resolved.shiftType().isBlank()) {
            return resolved.shiftType();
        }
        return employee.getShiftType();
    }

    private Optional<Timetable> resolveTimetableForDate(Employee employee, LocalDate date, Optional<Timetable> currentFallback) {
        if (employee == null) {
            return Optional.empty();
        }
        EmployeeShiftResolver.ResolvedShift resolved = employeeShiftResolver.resolve(employee, date);
        if (resolved.timetableId() != null) {
            if (currentFallback.isPresent() && Objects.equals(currentFallback.get().getId(), resolved.timetableId())) {
                return currentFallback;
            }
            return timetableRepository.findById(resolved.timetableId());
        }
        return currentFallback;
    }

    private String buildNotes(List<LeaveRequest> leaves, List<EmployeePermission> permissions, LocalDate date) {
        Optional<EmployeePermission> permission = permissions.stream()
                .filter(item -> !item.getStartDate().isAfter(date) && !item.getEndDate().isBefore(date))
                .findFirst();
        if (permission.isPresent()) {
            String reason = permission.get().getReason();
            return reason != null && !reason.isBlank() ? reason : "Approved permission";
        }

        boolean onLeave = leaves.stream()
                .anyMatch(item -> !item.getStartDate().isAfter(date) && !item.getEndDate().isBefore(date));
        return onLeave ? "Approved leave" : null;
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime localDateTime) {
        return AppTimeZone.toOffsetDateTime(localDateTime);
    }

    private List<AttendanceLog> findDayLogs(Long employeeId, LocalDate date) {
        // Fetch previous day too — night shifts that start yesterday may continue past midnight.
        List<AttendanceLog> candidates = attendanceLogRepository.findByEmployeeIdAndCheckInTimeBetween(
                employeeId,
                date.minusDays(1).atStartOfDay(),
                date.plusDays(1).atStartOfDay().minusNanos(1)
        );
        return candidates.stream()
                .filter(log -> attendanceInferenceService.overlapsDay(log, date))
                .toList();
    }

    private List<AttendanceSessionDTO> toSessionDtos(List<AttendanceInferenceService.SessionSegment> segments) {
        return segments.stream()
                .map(segment -> {
                    AttendanceSessionDTO session = new AttendanceSessionDTO();
                    session.setCheckInTime(toOffsetDateTime(segment.checkInTime()));
                    session.setCheckOutTime(toOffsetDateTime(segment.checkOutTime()));
                    return session;
                })
                .collect(Collectors.toList());
    }

    private ScheduleSettings resolveScheduleSettings(Employee employee, LocalDate date) {
        if (employee != null && employee.getTimetableId() != null) {
            Long tenantId = employee.getTenantId() != null ? employee.getTenantId() : TenantContext.getTenantId();
            Optional<Timetable> timetable = tenantId != null
                    ? timetableRepository.findByTenantIdAndId(tenantId, employee.getTimetableId())
                    : timetableRepository.findById(employee.getTimetableId());
            if (timetable.isPresent()) {
                Timetable value = timetable.get();
                return new ScheduleSettings(
                        value.getStartTime() != null ? value.getStartTime() : DEFAULT_SHIFT_START,
                        value.getEndTime() != null ? value.getEndTime() : DEFAULT_SHIFT_END,
                        value.getAllowedLateMinutes() != null ? value.getAllowedLateMinutes() : DEFAULT_ALLOWED_LATE_MINUTES
                );
            }
        }

        if (employee != null) {
            Optional<WorkSchedule> schedule = workScheduleRepository
                    .findTopByEmployeeIdAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(employee.getId(), date)
                    .filter(value -> value.getEndDate() == null || !value.getEndDate().isBefore(date));
            if (schedule.isPresent()) {
                WorkSchedule value = schedule.get();
                return new ScheduleSettings(
                        Optional.ofNullable(getScheduleStart(value, date.getDayOfWeek())).orElse(DEFAULT_SHIFT_START),
                        Optional.ofNullable(getScheduleEnd(value, date.getDayOfWeek())).orElse(DEFAULT_SHIFT_END),
                        value.getGracePeriodMinutes() != null ? value.getGracePeriodMinutes() : DEFAULT_ALLOWED_LATE_MINUTES
                );
            }
        }

        return new ScheduleSettings(DEFAULT_SHIFT_START, DEFAULT_SHIFT_END, DEFAULT_ALLOWED_LATE_MINUTES);
    }

    private AttendanceStatus determineStatus(LocalDate date,
                                             AttendanceInferenceService.AttendanceInference inference,
                                             ScheduleSettings scheduleSettings,
                                             String shiftType) {
        if (inference.firstEntry() == null) {
            return AttendanceStatus.ABSENT;
        }

        boolean flexible = ShiftTypes.isFlexible(shiftType);
        boolean late = !flexible
                && Duration.between(scheduleSettings.startTime(), inference.firstEntry().toLocalTime()).toMinutes()
                > scheduleSettings.allowedLateMinutes();

        if (!date.equals(AppTimeZone.today())) {
            return late ? AttendanceStatus.LATE : AttendanceStatus.PRESENT;
        }

        if (inference.currentlyInside()) {
            return late ? AttendanceStatus.LATE : AttendanceStatus.PRESENT;
        }

        if (flexible) {
            return inference.lastExit() != null ? AttendanceStatus.WORKDAY_COMPLETE : AttendanceStatus.PRESENT;
        }

        LocalDateTime scheduledEnd = date.atTime(scheduleSettings.endTime());
        if (inference.lastExit() != null && !inference.lastExit().isBefore(scheduledEnd)) {
            return AttendanceStatus.WORKDAY_COMPLETE;
        }

        return AttendanceStatus.ABSENT;
    }

    private record ScheduleSettings(LocalTime startTime, LocalTime endTime, int allowedLateMinutes) {
    }
}
