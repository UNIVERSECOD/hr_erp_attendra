package com.hic.service;

import com.hic.dto.AttendanceLogSyncDTO;
import com.hic.dto.DoorAttendanceSyncResultDTO;
import com.hic.model.AttendanceLog;
import com.hic.model.DeviceConfig;
import com.hic.model.Employee;
import com.hic.repository.AttendanceLogRepository;
import com.hic.repository.DeviceConfigRepository;
import com.hic.repository.EmployeeRepository;
import com.hic.util.AppTimeZone;
import com.hic.util.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class DoorAttendanceSyncService {

    private static final int DEFAULT_LIMIT = 500;

    private final AttendanceLogSyncService attendanceLogSyncService;
    private final AttendanceLogRepository attendanceLogRepository;
    private final EmployeeRepository employeeRepository;
    private final DeviceConfigRepository deviceConfigRepository;
    private final AttendanceCalculationService attendanceCalculationService;
    private final AttendanceService attendanceService;
    private final EmployeeShiftResolver employeeShiftResolver;

    /** Serializes sync per tenant so UI + scheduler cannot create duplicate sessions. */
    private final ConcurrentHashMap<Long, Object> tenantSyncLocks = new ConcurrentHashMap<>();
    private final Object nullTenantSyncLock = new Object();

    /** Spring proxy for transactional self-invocation; null in plain unit tests. */
    private DoorAttendanceSyncService self;

    @Autowired
    @Lazy
    void setSelf(DoorAttendanceSyncService self) {
        this.self = self;
    }

    /**
     * Entry-point for device → attendance_logs sync. Lock wraps the full transaction so
     * a second concurrent sync cannot race check-then-insert before the first commits.
     */
    public DoorAttendanceSyncResultDTO syncAllDevices(
            LocalDateTime start,
            LocalDateTime end,
            Integer limit
    ) {
        Long tenantId = TenantContext.getTenantId();
        Object lock = tenantId != null
                ? tenantSyncLocks.computeIfAbsent(tenantId, ignored -> new Object())
                : nullTenantSyncLock;
        synchronized (lock) {
            DoorAttendanceSyncService runner = self != null ? self : this;
            return runner.syncAllDevicesInTransaction(start, end, limit);
        }
    }

    @Transactional
    public DoorAttendanceSyncResultDTO syncAllDevicesInTransaction(
            LocalDateTime start,
            LocalDateTime end,
            Integer limit
    ) {
        int effectiveLimit = limit == null || limit <= 0 ? DEFAULT_LIMIT : limit;
        Long tenantId = TenantContext.getTenantId();

        List<DeviceConfig> devices = tenantId != null
                ? deviceConfigRepository.findByTenantId(tenantId)
                : deviceConfigRepository.findAll();

        List<DeviceConfig> activeDevicesWithRoles = devices.stream()
                .filter(d -> "ACTIVE".equalsIgnoreCase(d.getStatus()))
                .filter(d -> "ENTRY".equalsIgnoreCase(d.getDoorRole()) || "EXIT".equalsIgnoreCase(d.getDoorRole()))
                .toList();

        List<AttendanceLogSyncDTO.AttendanceLogEntryDTO> allPunches = new ArrayList<>();
        Map<Long, String> deviceRolesByIsapiId = new HashMap<>();
        Map<Long, Long> backendIdByIsapiId = new HashMap<>();

        for (DeviceConfig device : activeDevicesWithRoles) {
            Long isapiDeviceId = toIsapiDeviceId(device);
            if (isapiDeviceId == null) {
                log.warn("Skipping device config id={} — missing/invalid ISAPI deviceId={}", device.getId(), device.getDeviceId());
                continue;
            }
            deviceRolesByIsapiId.put(isapiDeviceId, device.getDoorRole());
            backendIdByIsapiId.put(isapiDeviceId, device.getId());
            allPunches.addAll(fetchPunchesInRange(isapiDeviceId, start, end, effectiveLimit)
                    .stream()
                    .filter(p -> p.getPunchTime() != null && p.getEmployeeNo() != null)
                    .toList());
        }

        Set<String> unresolvedEmployeeNos = new HashSet<>();
        Map<Long, Set<LocalDate>> recalcDatesByEmployee = new HashMap<>();

        // Resolve each punch to an employee using device + device person ID
        // so the same raw employeeNo at two branches can map to different people.
        Map<Long, List<AttendanceLogSyncDTO.AttendanceLogEntryDTO>> punchesByEmployeeId = new HashMap<>();

        int matchedSessions = 0;
        int createdLogs = 0;
        int skippedEmployees = 0;
        int skippedPunches = 0;

        for (AttendanceLogSyncDTO.AttendanceLogEntryDTO punch : allPunches) {
            String code = normalizeEmployeeCode(punch.getEmployeeNo());
            if (code == null) {
                skippedPunches++;
                continue;
            }
            Long backendDeviceConfigId = punch.getDeviceId() != null
                    ? backendIdByIsapiId.getOrDefault(punch.getDeviceId(), punch.getDeviceId())
                    : null;
            Employee employee = resolveEmployee(tenantId, backendDeviceConfigId, punch.getEmployeeNo());
            if (employee == null) {
                skippedPunches++;
                unresolvedEmployeeNos.add(code);
                continue;
            }
            punchesByEmployeeId.computeIfAbsent(employee.getId(), ignored -> new ArrayList<>()).add(punch);
        }

        skippedEmployees = unresolvedEmployeeNos.size();

        for (Map.Entry<Long, List<AttendanceLogSyncDTO.AttendanceLogEntryDTO>> entry : punchesByEmployeeId.entrySet()) {
            Employee employee = tenantId != null
                    ? employeeRepository.findByTenantIdAndId(tenantId, entry.getKey()).orElse(null)
                    : employeeRepository.findById(entry.getKey()).orElse(null);
            if (employee == null) {
                continue;
            }

            List<AttendanceLogSyncDTO.AttendanceLogEntryDTO> employeePunches = entry.getValue().stream()
                    .sorted(Comparator.comparing(p -> toLocalDateTime(p.getPunchTime())))
                    .toList();

            // Resume an already-open session from DB (entry punched earlier, exit comes later).
            AttendanceLog openLog = findOpenSession(tenantId, employee.getId()).orElse(null);
            AttendanceLog lastClosedLog = null;

            for (AttendanceLogSyncDTO.AttendanceLogEntryDTO punch : employeePunches) {
                LocalDateTime punchTime = toLocalDateTime(punch.getPunchTime());
                String role = deviceRolesByIsapiId.get(punch.getDeviceId());

                if ("ENTRY".equals(role)) {
                    if (openLog != null) {
                        // Duplicate giriş while still open → first punch was invalid; move check-in forward.
                        LocalDateTime previousEntry = openLog.getCheckInTime();
                        if (previousEntry != null && !punchTime.isAfter(previousEntry)) {
                            continue;
                        }
                        addRecalcDate(recalcDatesByEmployee, employee.getId(),
                                previousEntry != null ? previousEntry.toLocalDate() : null);
                        openLog.setCheckInTime(punchTime);
                        openLog.setDeviceId(punch.getDeviceId() != null ? String.valueOf(punch.getDeviceId()) : openLog.getDeviceId());
                        openLog.setDoorId(punch.getDeviceId() != null ? punch.getDeviceId() + ":null" : openLog.getDoorId());
                        attendanceLogRepository.save(openLog);
                        createdLogs++;
                        addRecalcDate(recalcDatesByEmployee, employee.getId(), punchTime.toLocalDate());
                        continue;
                    }

                    // New giriş → create open log immediately (çıxış later updates the same row).
                    AttendanceLog created = ensureOpenEntryLog(
                            tenantId,
                            employee,
                            punchTime,
                            punch.getDeviceId()
                    );
                    if (created != null) {
                        createdLogs++;
                        addRecalcDate(recalcDatesByEmployee, employee.getId(), punchTime.toLocalDate());
                        openLog = created;
                    } else {
                        openLog = tenantId != null
                                ? attendanceLogRepository.findByTenantIdAndEmployeeIdAndCheckInTime(
                                        tenantId, employee.getId(), punchTime).orElse(null)
                                : attendanceLogRepository.findByEmployeeIdAndCheckInTime(
                                        employee.getId(), punchTime).orElse(null);
                        if (openLog == null) {
                            openLog = findOpenSession(tenantId, employee.getId()).orElse(null);
                        }
                    }
                } else if ("EXIT".equals(role)) {
                    if (openLog != null) {
                        LocalDateTime entryTime = openLog.getCheckInTime();
                        if (entryTime == null || !punchTime.isAfter(entryTime)) {
                            continue;
                        }

                        Long entryDevId = openLog.getDeviceId() != null
                                ? parseLongSafe(openLog.getDeviceId())
                                : null;
                        Long exitDevId = punch.getDeviceId();
                        String doorId = entryDevId + ":" + exitDevId;

                        addRecalcDate(recalcDatesByEmployee, employee.getId(), entryTime.toLocalDate());
                        addRecalcDate(recalcDatesByEmployee, employee.getId(), punchTime.toLocalDate());

                        if (openLog.getCheckOutTime() == null) {
                            openLog.setCheckOutTime(punchTime);
                            openLog.setDoorId(doorId);
                            attendanceLogRepository.save(openLog);
                            createdLogs++;
                            matchedSessions++;
                        }

                        lastClosedLog = openLog;
                        openLog = null;
                    } else {
                        // Duplicate çıxış while already outside → employee stayed inside; extend last checkout.
                        AttendanceLog closed = lastClosedLog != null
                                ? lastClosedLog
                                : findLastClosedSession(tenantId, employee.getId()).orElse(null);
                        if (closed == null
                                || closed.getCheckInTime() == null
                                || closed.getCheckOutTime() == null
                                || !punchTime.isAfter(closed.getCheckInTime())
                                || !punchTime.isAfter(closed.getCheckOutTime())) {
                            continue;
                        }

                        LocalDateTime previousExit = closed.getCheckOutTime();
                        addRecalcDate(recalcDatesByEmployee, employee.getId(), closed.getCheckInTime().toLocalDate());
                        addRecalcDate(recalcDatesByEmployee, employee.getId(), previousExit.toLocalDate());
                        addRecalcDate(recalcDatesByEmployee, employee.getId(), punchTime.toLocalDate());

                        Long entryDevId = closed.getDeviceId() != null
                                ? parseLongSafe(closed.getDeviceId())
                                : null;
                        closed.setCheckOutTime(punchTime);
                        if (punch.getDeviceId() != null) {
                            closed.setDoorId(entryDevId + ":" + punch.getDeviceId());
                        }
                        attendanceLogRepository.save(closed);
                        lastClosedLog = closed;
                        createdLogs++;
                        matchedSessions++;
                    }
                }
            }
        }

        int recalculatedDays = 0;
        for (Map.Entry<Long, Set<LocalDate>> entry : recalcDatesByEmployee.entrySet()) {
            List<LocalDate> dates = new ArrayList<>(entry.getValue());
            dates.sort(Comparator.naturalOrder());
            for (LocalDate date : dates) {
                attendanceCalculationService.calculateForDay(entry.getKey(), date);
                attendanceService.generateDailySummary(entry.getKey(), date);
                recalculatedDays++;
            }
        }

        if (!unresolvedEmployeeNos.isEmpty()) {
            log.warn("Auto attendance sync unresolved employeeNo values: {} ({} punches skipped)",
                    unresolvedEmployeeNos, skippedPunches);
        }

        return new DoorAttendanceSyncResultDTO(
                allPunches.size(),
                matchedSessions,
                createdLogs,
                skippedEmployees,
                skippedPunches,
                recalculatedDays,
                unresolvedEmployeeNos.stream().sorted().toList()
        );
    }

    private Optional<AttendanceLog> findOpenSession(Long tenantId, Long employeeId) {
        if (tenantId != null) {
            return attendanceLogRepository
                    .findFirstByTenantIdAndEmployeeIdAndCheckOutTimeIsNullOrderByCheckInTimeDesc(tenantId, employeeId);
        }
        return attendanceLogRepository.findFirstByEmployeeIdAndCheckOutTimeIsNullOrderByCheckInTimeDesc(employeeId);
    }

    private Optional<AttendanceLog> findLastClosedSession(Long tenantId, Long employeeId) {
        if (tenantId != null) {
            return attendanceLogRepository
                    .findFirstByTenantIdAndEmployeeIdAndCheckOutTimeIsNotNullOrderByCheckOutTimeDesc(tenantId, employeeId);
        }
        return attendanceLogRepository.findFirstByEmployeeIdAndCheckOutTimeIsNotNullOrderByCheckOutTimeDesc(employeeId);
    }

    /**
     * Creates an open entry log (no checkout yet) if one does not already exist for this check-in time.
     * Returns the created entity, or null when the log already existed.
     */
    private AttendanceLog ensureOpenEntryLog(Long tenantId, Employee employee, LocalDateTime entryTime, Long entryDeviceId) {
        Optional<AttendanceLog> existing = tenantId != null
                ? attendanceLogRepository.findByTenantIdAndEmployeeIdAndCheckInTime(tenantId, employee.getId(), entryTime)
                : attendanceLogRepository.findByEmployeeIdAndCheckInTime(employee.getId(), entryTime);
        if (existing.isPresent()) {
            return null;
        }

        AttendanceLog logEntry = new AttendanceLog();
        logEntry.setTenantId(tenantId != null ? tenantId : employee.getTenantId());
        logEntry.setEmployeeId(employee.getId());
        logEntry.setCheckInTime(entryTime);
        logEntry.setCheckOutTime(null);
        logEntry.setDoorId(entryDeviceId != null ? entryDeviceId + ":null" : null);
        logEntry.setDeviceId(entryDeviceId != null ? String.valueOf(entryDeviceId) : null);
        logEntry.setEventType("DOOR_SESSION");
        logEntry.setVerificationMethod("ISAPI_PUNCH");
        logEntry.setStatus("ACTIVE");
        EmployeeShiftResolver.ResolvedShift resolved = employeeShiftResolver.resolve(
                employee, entryTime != null ? entryTime.toLocalDate() : null);
        logEntry.setShiftType(resolved.shiftType());
        logEntry.setTimetableId(resolved.timetableId());
        return attendanceLogRepository.save(logEntry);
    }

    private Long parseLongSafe(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private List<AttendanceLogSyncDTO.AttendanceLogEntryDTO> fetchPunchesInRange(
            Long deviceId,
            LocalDateTime start,
            LocalDateTime end,
            int pageSize
    ) {
        List<AttendanceLogSyncDTO.AttendanceLogEntryDTO> punches = new ArrayList<>();
        OffsetDateTime rangeStart = start == null
                ? AppTimeZone.nowOffset().minusDays(7)
                : start.atZone(AppTimeZone.ZONE).toOffsetDateTime();
        OffsetDateTime rangeEnd = end == null
                ? AppTimeZone.nowOffset()
                : end.atZone(AppTimeZone.ZONE).toOffsetDateTime();

        int page = 0;
        while (true) {
            List<AttendanceLogSyncDTO.AttendanceLogEntryDTO> batch = attendanceLogSyncService.getAttendanceLogs(
                    deviceId,
                    null,
                    rangeStart,
                    rangeEnd,
                    page,
                    pageSize
            );
            if (batch.isEmpty()) {
                break;
            }
            punches.addAll(batch);
            if (batch.size() < pageSize) {
                break;
            }
            page++;
        }
        return punches;
    }

    private LocalDateTime toLocalDateTime(OffsetDateTime time) {
        return AppTimeZone.toLocalDateTime(time);
    }

    private Employee resolveEmployee(Long tenantId, Long deviceConfigId, String employeeCode) {
        String normalizedCode = employeeCode == null ? null : employeeCode.trim();
        if (normalizedCode == null || normalizedCode.isEmpty()) {
            return null;
        }

        if (deviceConfigId != null) {
            List<Employee> byAccess = employeeRepository.findByDeviceAccessAndDeviceEmployeeNo(
                    deviceConfigId, normalizedCode);
            if (byAccess.size() == 1) {
                return byAccess.get(0);
            }
            if (byAccess.size() > 1) {
                log.warn("Multiple employees match deviceConfigId={} deviceEmployeeNo={}", deviceConfigId, normalizedCode);
                return byAccess.get(0);
            }

            DeviceConfig device = deviceConfigRepository.findById(deviceConfigId).orElse(null);
            if (device != null && device.getBranchId() != null && tenantId != null) {
                List<Employee> byHomeBranch = employeeRepository.findByTenantIdAndBranchIdAndDeviceEmployeeNoIgnoreCase(
                        tenantId, device.getBranchId(), normalizedCode);
                if (byHomeBranch.size() == 1) {
                    return byHomeBranch.get(0);
                }
                if (byHomeBranch.size() > 1) {
                    return byHomeBranch.get(0);
                }
            }
        }

        // Legacy / prefixed employeeId exact match (BAK-1001 or raw 1001)
        Employee byEmployeeCode = tenantId != null
                ? employeeRepository.findByTenantIdAndEmployeeId(tenantId, normalizedCode)
                .or(() -> employeeRepository.findByTenantIdAndEmployeeIdIgnoreCase(tenantId, normalizedCode))
                .orElse(null)
                : employeeRepository.findByEmployeeId(normalizedCode)
                .or(() -> employeeRepository.findByEmployeeIdIgnoreCase(normalizedCode))
                .orElse(null);
        if (byEmployeeCode != null) {
            return byEmployeeCode;
        }

        List<Employee> byDeviceNo = tenantId != null
                ? employeeRepository.findByTenantIdAndDeviceEmployeeNoIgnoreCase(tenantId, normalizedCode)
                : employeeRepository.findByDeviceEmployeeNoIgnoreCase(normalizedCode);
        if (byDeviceNo.size() == 1) {
            return byDeviceNo.get(0);
        }

        if (normalizedCode.chars().allMatch(Character::isDigit)) {
            try {
                Long employeePk = Long.parseLong(normalizedCode);
                return tenantId != null
                        ? employeeRepository.findByTenantIdAndId(tenantId, employeePk).orElse(null)
                        : employeeRepository.findById(employeePk).orElse(null);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private int countPunchesForEmployeeCode(List<AttendanceLogSyncDTO.AttendanceLogEntryDTO> punches, String employeeCode) {
        return (int) punches.stream()
                .filter(p -> employeeCode.equals(normalizeEmployeeCode(p.getEmployeeNo())))
                .count();
    }

    private int countPunchesForEmployeeCode(
            List<AttendanceLogSyncDTO.AttendanceLogEntryDTO> entryPunches,
            List<AttendanceLogSyncDTO.AttendanceLogEntryDTO> exitPunches,
            String employeeCode
    ) {
        return countPunchesForEmployeeCode(entryPunches, employeeCode)
                + countPunchesForEmployeeCode(exitPunches, employeeCode);
    }

    private String normalizeEmployeeCode(String employeeCode) {
        return employeeCode == null ? null : employeeCode.trim().toUpperCase();
    }

    /**
     * ISAPI punch APIs use the ISAPI-side device primary key stored in {@code device_configs.device_id},
     * not the backend {@code device_configs.id}.
     */
    private Long toIsapiDeviceId(DeviceConfig device) {
        if (device == null) {
            return null;
        }
        if (device.getDeviceId() != null && !device.getDeviceId().isBlank()) {
            try {
                return Long.valueOf(device.getDeviceId().trim());
            } catch (NumberFormatException ignored) {
                log.warn("Invalid ISAPI deviceId on device config id={}: {}", device.getId(), device.getDeviceId());
            }
        }
        return device.getId();
    }

    private void addRecalcDate(Map<Long, Set<LocalDate>> map, Long employeeId, LocalDate date) {
        if (employeeId == null || date == null) {
            return;
        }
        map.computeIfAbsent(employeeId, ignored -> new HashSet<>()).add(date);
    }
}
