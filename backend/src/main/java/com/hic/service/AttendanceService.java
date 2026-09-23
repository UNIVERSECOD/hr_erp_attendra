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
import com.hic.repository.AttendanceLogRepository;
import com.hic.repository.DailyAttendanceSummaryRepository;
import com.hic.repository.EmployeePermissionRepository;
import com.hic.repository.EmployeeRepository;
import com.hic.repository.LeaveRequestRepository;
import com.hic.util.AppTimeZone;
import com.hic.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final UserScopeService userScopeService;
    private final AttendanceInferenceService attendanceInferenceService;
    private final AttendanceScheduleResolver attendanceScheduleResolver;
    private final AttendanceTimeCalculator attendanceTimeCalculator;

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
        AttendanceScheduleResolver.DaySchedule daySchedule = attendanceScheduleResolver.resolve(employee, date);
        AttendanceTimeCalculator.Calculation calculation = attendanceTimeCalculator.calculate(
                date, inference, daySchedule);

        Optional<DailyAttendanceSummary> existing = summaryRepository.findByEmployeeIdAndAttendanceDate(employeeId, date);
        DailyAttendanceSummary summary = existing.orElse(new DailyAttendanceSummary());
        summary.setTenantId(resolveAttendanceTenantId(employeeId));
        summary.setEmployeeId(employeeId);
        summary.setAttendanceDate(date);
        summary.setIsHoliday(false);
        summary.setIsLeave(false);
        summary.setIsStandardDay(daySchedule.workingDay());
        boolean workedOnDayOff = !daySchedule.workingDay() && inference.firstEntry() != null;
        summary.setIsAdditionalDay(workedOnDayOff);
        summary.setIsExtraDay(workedOnDayOff);

        summary.setCheckInTime(inference.firstEntry());
        summary.setCheckOutTime(inference.lastExit());
        summary.setHoursWorked(calculation.workedMinutes() / 60.0);
        summary.setLateMinutes(calculation.lateMinutes());
        summary.setEarlyLeaveMinutes(calculation.earlyLeaveMinutes());
        summary.setAttendanceStatus(calculation.status());

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
            LocalDateTime firstCheckIn = inference.firstEntry();
            LocalDateTime lastCheckOut = inference.lastExit();
            AttendanceScheduleResolver.DaySchedule daySchedule = attendanceScheduleResolver.resolve(employee, currentDate);
            AttendanceTimeCalculator.Calculation calculation = attendanceTimeCalculator.calculate(
                    currentDate, inference, daySchedule);
            double hoursWorked = calculation.workedMinutes() / 60.0;
            int lateMinutes = calculation.lateMinutes();
            int earlyLeaveMinutes = calculation.earlyLeaveMinutes();
            AttendanceStatus attendanceStatus = calculation.status();

            if (summary != null && summary.getHoursWorked() != null && dayLogs.isEmpty()) {
                hoursWorked = summary.getHoursWorked();
                firstCheckIn = summary.getCheckInTime();
                lastCheckOut = summary.getCheckOutTime();
                lateMinutes = summary.getLateMinutes() != null ? summary.getLateMinutes() : 0;
                earlyLeaveMinutes = summary.getEarlyLeaveMinutes() != null ? summary.getEarlyLeaveMinutes() : 0;
                if (summary.getAttendanceStatus() != null) {
                    attendanceStatus = summary.getAttendanceStatus();
                }
            }

            EmployeeAttendanceRowDTO row = new EmployeeAttendanceRowDTO();
            row.setDate(currentDate);
            row.setCheckInTime(toOffsetDateTime(firstCheckIn));
            row.setCheckOutTime(toOffsetDateTime(lastCheckOut));
            row.setHoursWorked(hoursWorked);
            row.setLateMinutes(lateMinutes);
            row.setEarlyLeaveMinutes(earlyLeaveMinutes);
            row.setStatus(onLeave ? AttendanceStatus.ON_LEAVE : attendanceStatus);
            row.setNotes(buildNotes(approvedLeaves, approvedPermissions, currentDate));
            row.setShiftType(daySchedule.shiftType());
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
                        || row.getStatus() == AttendanceStatus.EARLY_LEAVE
                        || row.getStatus() == AttendanceStatus.WORKDAY_COMPLETE)
                .count());
        summary.setTotalHours(rows.stream()
                .map(EmployeeAttendanceRowDTO::getHoursWorked)
                .filter(java.util.Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum());
        summary.setAbsentDays(rows.stream().filter(row -> row.getStatus() == AttendanceStatus.ABSENT).count());
        summary.setLateDays(rows.stream().filter(row -> row.getLateMinutes() != null && row.getLateMinutes() > 0).count());
        summary.setLeaveDays(rows.stream().filter(row -> row.getStatus() == AttendanceStatus.ON_LEAVE).count());
        return summary;
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
        dto.setLateMinutes(s.getLateMinutes());
        dto.setEarlyLeaveMinutes(s.getEarlyLeaveMinutes());
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

    private boolean overlapsLeave(List<LeaveRequest> leaves, LocalDate date) {
        return leaves.stream().anyMatch(leave ->
                !leave.getStartDate().isAfter(date) && !leave.getEndDate().isBefore(date));
    }

    private boolean overlapsPermission(List<EmployeePermission> permissions, LocalDate date) {
        return permissions.stream().anyMatch(permission ->
                !permission.getStartDate().isAfter(date) && !permission.getEndDate().isBefore(date));
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

}
