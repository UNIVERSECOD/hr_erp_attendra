package com.hic.service;

import com.hic.dto.AttendanceReportRowDTO;
import com.hic.dto.PaginatedResponse;
import com.hic.model.AttendanceLog;
import com.hic.model.Department;
import com.hic.model.Employee;
import com.hic.model.Position;
import com.hic.model.Timetable;
import com.hic.repository.AttendanceLogRepository;
import com.hic.repository.DepartmentRepository;
import com.hic.repository.EmployeeRepository;
import com.hic.repository.FaceDataRepository;
import com.hic.repository.PositionRepository;
import com.hic.repository.TimetableRepository;
import com.hic.util.AppTimeZone;
import com.hic.util.ShiftTypes;
import com.hic.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttendanceReportService {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AttendanceLogRepository attendanceLogRepository;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;
    private final FaceDataRepository faceDataRepository;
    private final TimetableRepository timetableRepository;
    private final AttendanceInferenceService attendanceInferenceService;
    private final EmployeeShiftResolver employeeShiftResolver;

    public PaginatedResponse<AttendanceReportRowDTO> getReport(
            LocalDate start,
            LocalDate end,
            String shiftType,
            String employeeCode,
            String name,
            String fin,
            String position,
            String department,
            String area,
            int page,
            int size
    ) {
        List<AttendanceReportRowDTO> allRows = queryRows(
                start, end, shiftType, employeeCode, name, fin, position, department, area
        );
        int fromIndex = Math.min(page * size, allRows.size());
        int toIndex = Math.min(fromIndex + size, allRows.size());
        int totalPages = size > 0 ? (int) Math.ceil((double) allRows.size() / size) : 0;
        return PaginatedResponse.of(allRows.subList(fromIndex, toIndex), allRows.size(), totalPages, page, size);
    }

    public byte[] exportExcel(
            LocalDate start,
            LocalDate end,
            String shiftType,
            String employeeCode,
            String name,
            String fin,
            String position,
            String department,
            String area
    ) {
        List<AttendanceReportRowDTO> rows = queryRows(start, end, shiftType, employeeCode, name, fin, position, department, area);
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Attendance Reports");
            Row header = sheet.createRow(0);
            String[] headers = {
                    "ID", "Name", "FIN", "Department", "Position", "Area",
                    "Check-in", "Check-out", "Worked", "Method", "Shift"
            };
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
                sheet.setColumnWidth(i, i == 6 || i == 7 ? 6500 : 5500);
            }

            int rowNum = 1;
            for (AttendanceReportRowDTO row : rows) {
                Row excelRow = sheet.createRow(rowNum++);
                excelRow.createCell(0).setCellValue(safe(row.getEmployeeId()));
                excelRow.createCell(1).setCellValue(safe(row.getFullName()));
                excelRow.createCell(2).setCellValue(safe(row.getFin()));
                excelRow.createCell(3).setCellValue(safe(row.getDepartment()));
                excelRow.createCell(4).setCellValue(safe(row.getPosition()));
                excelRow.createCell(5).setCellValue(safe(row.getArea()));
                excelRow.createCell(6).setCellValue(formatDateTime(row.getCheckInTime()));
                excelRow.createCell(7).setCellValue(formatDateTime(row.getCheckOutTime()));
                excelRow.createCell(8).setCellValue(formatDuration(row.getWorkedMinutes()));
                excelRow.createCell(9).setCellValue(safe(row.getVerificationMethod()));
                excelRow.createCell(10).setCellValue(safe(row.getShiftType()));
            }
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to export attendance report", e);
        }
    }

    private List<AttendanceReportRowDTO> queryRows(
            LocalDate start,
            LocalDate end,
            String shiftType,
            String employeeCode,
            String name,
            String fin,
            String position,
            String department,
            String area
    ) {
        Long tenantId = TenantContext.getTenantId();

        // Include previous day so night shifts that cross midnight into `start` are counted.
        LocalDateTime startDt = start.minusDays(1).atStartOfDay();
        LocalDateTime endDt = end.atTime(LocalTime.MAX);

        List<AttendanceLog> logs = tenantId != null
                ? attendanceLogRepository.findByTenantIdAndCheckInTimeBetween(tenantId, startDt, endDt)
                : attendanceLogRepository.findByCheckInTimeBetween(startDt, endDt);

        Set<Long> employeeIds = logs.stream()
                .map(AttendanceLog::getEmployeeId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, Employee> employeeMap = employeeRepository.findAllById(employeeIds).stream()
                .collect(Collectors.toMap(Employee::getId, e -> e));

        Map<Long, List<com.hic.model.EmployeeShiftAssignment>> assignmentsByEmployee = tenantId != null
                ? employeeShiftResolver.loadAssignmentsByEmployee(tenantId, employeeIds, start.minusDays(1), end)
                : Map.of();

        Set<Long> timetableIds = employeeShiftResolver.collectTimetableIds(employeeMap.values(), assignmentsByEmployee);
        Map<Long, Timetable> timetableMap = timetableIds.isEmpty()
                ? Map.of()
                : timetableRepository.findAllById(timetableIds).stream()
                .collect(Collectors.toMap(Timetable::getId, t -> t, (left, right) -> left, HashMap::new));

        Set<Long> departmentIds = employeeMap.values().stream()
                .map(Employee::getDepartmentId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> positionIds = employeeMap.values().stream()
                .map(Employee::getPositionId).filter(Objects::nonNull).collect(Collectors.toSet());

        Map<Long, String> departmentNames = departmentRepository.findAllById(departmentIds).stream()
                .collect(Collectors.toMap(Department::getId, Department::getDepartmentName));
        Map<Long, String> positionNames = positionRepository.findAllById(positionIds).stream()
                .collect(Collectors.toMap(Position::getId, Position::getPositionName));

        Map<Long, List<AttendanceLog>> logsByEmployee = logs.stream()
                .filter(log -> log.getEmployeeId() != null && log.getCheckInTime() != null)
                .collect(Collectors.groupingBy(AttendanceLog::getEmployeeId));

        Predicate<AttendanceReportRowDTO> predicate = dto ->
                contains(dto.getEmployeeId(), employeeCode) &&
                        contains(dto.getFullName(), name) &&
                        contains(dto.getFin(), fin) &&
                        contains(dto.getPosition(), position) &&
                        contains(dto.getDepartment(), department) &&
                        contains(dto.getArea(), area) &&
                        ShiftTypes.matchesFilter(dto.getShiftType(), shiftType);

        List<AttendanceReportRowDTO> rows = new ArrayList<>();
        for (Map.Entry<Long, List<AttendanceLog>> entry : logsByEmployee.entrySet()) {
            Employee employee = employeeMap.get(entry.getKey());
            if (employee == null) {
                continue;
            }

            List<AttendanceLog> employeeLogs = attendanceInferenceService.dedupeSessions(
                    entry.getValue().stream()
                            .filter(log -> log.getCheckInTime() != null)
                            .filter(log -> overlapsReportRange(log, start, end))
                            .sorted(Comparator.comparing(AttendanceLog::getCheckInTime)
                                    .thenComparing(log -> log.getId() != null ? log.getId() : 0L))
                            .toList()
            );

            List<LabeledSession> sessionLogs = new ArrayList<>();
            Map<LocalDate, List<AttendanceLog>> standardByDay = new HashMap<>();
            Map<LocalDate, String> standardShiftTypeByDay = new HashMap<>();

            for (AttendanceLog log : employeeLogs) {
                EmployeeShiftResolver.ResolvedShift resolved = employeeShiftResolver.resolveForLog(
                        employee, log, assignmentsByEmployee, timetableMap);
                String scheduleShiftType = resolved.shiftType() != null ? resolved.shiftType() : employee.getShiftType();
                boolean knownStandard = resolved.knownFromHistory()
                        && ShiftTypes.STANDARD.equals(ShiftTypes.canonical(resolved.shiftType()));

                if (knownStandard) {
                    // Only collapse when we KNOW this punch belonged to STANDARD (stamp or assignment).
                    for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
                        if (!attendanceInferenceService.overlapsDay(log, day)) {
                            continue;
                        }
                        standardByDay.computeIfAbsent(day, ignored -> new ArrayList<>()).add(log);
                        standardShiftTypeByDay.putIfAbsent(day, scheduleShiftType);
                    }
                } else {
                    // Flexible, night, or unmarked history after schedule change: keep every session.
                    sessionLogs.add(new LabeledSession(log, scheduleShiftType));
                }
            }

            for (LabeledSession labeled : sessionLogs) {
                AttendanceReportRowDTO dto = buildSessionRow(
                        employee, labeled.log(), labeled.shiftType(), departmentNames, positionNames);
                if (predicate.test(dto)) {
                    rows.add(dto);
                }
            }

            // STANDARD: one row per calendar day — first entry / last exit.
            for (Map.Entry<LocalDate, List<AttendanceLog>> dayEntry : standardByDay.entrySet()) {
                LocalDate day = dayEntry.getKey();
                AttendanceInferenceService.AttendanceInference inference =
                        attendanceInferenceService.inferDay(dayEntry.getValue(), day);
                if (inference.firstEntry() == null && inference.lastExit() == null && !inference.currentlyInside()) {
                    continue;
                }
                String dayShiftType = standardShiftTypeByDay.getOrDefault(day, ShiftTypes.STANDARD);
                AttendanceReportRowDTO dto = buildDailyRow(
                        employee,
                        day,
                        inference,
                        dayShiftType,
                        dayEntry.getValue(),
                        departmentNames,
                        positionNames
                );
                if (predicate.test(dto)) {
                    rows.add(dto);
                }
            }
        }

        return rows.stream()
                .sorted(Comparator.comparing(AttendanceReportRowDTO::getDate, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(AttendanceReportRowDTO::getCheckInTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private record LabeledSession(AttendanceLog log, String shiftType) {
    }

    private AttendanceReportRowDTO buildSessionRow(
            Employee employee,
            AttendanceLog log,
            String scheduleShiftType,
            Map<Long, String> departmentNames,
            Map<Long, String> positionNames
    ) {
        AttendanceReportRowDTO dto = baseEmployeeRow(employee, departmentNames, positionNames);
        dto.setAttendanceLogId(log.getId());
        dto.setDate(log.getCheckInTime().toLocalDate());
        dto.setCheckInTime(toOffsetDateTime(log.getCheckInTime()));
        dto.setCheckOutTime(toOffsetDateTime(log.getCheckOutTime()));
        if (log.getCheckOutTime() != null && log.getCheckOutTime().isAfter(log.getCheckInTime())) {
            dto.setWorkedMinutes((int) Duration.between(log.getCheckInTime(), log.getCheckOutTime()).toMinutes());
        } else {
            dto.setWorkedMinutes(0);
        }
        dto.setVerificationMethod(normalizeVerificationMethod(log.getVerificationMethod()));
        dto.setShiftType(scheduleShiftType);
        return dto;
    }

    private AttendanceReportRowDTO buildDailyRow(
            Employee employee,
            LocalDate day,
            AttendanceInferenceService.AttendanceInference inference,
            String scheduleShiftType,
            List<AttendanceLog> dayLogs,
            Map<Long, String> departmentNames,
            Map<Long, String> positionNames
    ) {
        AttendanceReportRowDTO dto = baseEmployeeRow(employee, departmentNames, positionNames);
        Long firstLogId = dayLogs.stream()
                .filter(l -> l.getCheckInTime() != null)
                .min(Comparator.comparing(AttendanceLog::getCheckInTime)
                        .thenComparing(l -> l.getId() != null ? l.getId() : 0L))
                .map(AttendanceLog::getId)
                .orElse(null);
        dto.setAttendanceLogId(firstLogId);
        dto.setDate(day);
        dto.setCheckInTime(toOffsetDateTime(inference.firstEntry()));
        dto.setCheckOutTime(toOffsetDateTime(inference.lastExit()));
        dto.setWorkedMinutes(inference.workedMinutesForShift(scheduleShiftType));
        String method = dayLogs.stream()
                .map(AttendanceLog::getVerificationMethod)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        dto.setVerificationMethod(normalizeVerificationMethod(method));
        dto.setShiftType(scheduleShiftType);
        return dto;
    }

    private AttendanceReportRowDTO baseEmployeeRow(
            Employee employee,
            Map<Long, String> departmentNames,
            Map<Long, String> positionNames
    ) {
        AttendanceReportRowDTO dto = new AttendanceReportRowDTO();
        dto.setEmployeePk(employee.getId());
        dto.setEmployeeId(employee.getEmployeeId());
        dto.setFullName((safe(employee.getFirstName()) + " " + safe(employee.getLastName())).trim());
        dto.setFin(employee.getFinNumber());
        dto.setDepartment(departmentNames.get(employee.getDepartmentId()));
        dto.setPosition(positionNames.get(employee.getPositionId()));
        dto.setArea(employee.getArea());
        faceDataRepository.findTopByEmployeeIdOrderByCreatedAtDesc(employee.getId())
                .ifPresent(face -> dto.setPhotoUrl("/api/faces/employee/" + employee.getId() + "/image"));
        return dto;
    }

    /**
     * True when the session interval overlaps any calendar day in {@code [start, end]}.
     * Open sessions are limited to check-ins on/after {@code start - 1 day} so ancient
     * unfinished sessions do not flood every report window.
     */
    private boolean overlapsReportRange(AttendanceLog log, LocalDate start, LocalDate end) {
        LocalDateTime entry = log.getCheckInTime();
        if (entry == null) {
            return false;
        }
        LocalDateTime exit = log.getCheckOutTime();
        if (exit == null) {
            LocalDate inDate = entry.toLocalDate();
            return !inDate.isBefore(start.minusDays(1)) && !inDate.isAfter(end);
        }
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            if (attendanceInferenceService.overlapsDay(log, day)) {
                return true;
            }
        }
        return false;
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime localDateTime) {
        return AppTimeZone.toOffsetDateTime(localDateTime);
    }

    private boolean contains(String value, String filter) {
        if (filter == null || filter.isBlank()) {
            return true;
        }
        return safe(value).toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String formatDuration(Integer workedMinutes) {
        if (workedMinutes == null || workedMinutes <= 0) {
            return "";
        }
        int hours = workedMinutes / 60;
        int minutes = workedMinutes % 60;
        return String.format("%02d:%02d", hours, minutes);
    }

    /** Full date+time so overnight flexible/night sessions show distinct calendar days. */
    private String formatDateTime(OffsetDateTime value) {
        if (value == null) {
            return "";
        }
        return value.toLocalDateTime().withNano(0).format(DATE_TIME_FORMAT);
    }

    private String normalizeVerificationMethod(String method) {
        if (method == null || method.isBlank()) {
            return null;
        }
        String lower = method.trim().toLowerCase(Locale.ROOT);
        if (lower.contains("face")) {
            return "face";
        }
        if (lower.contains("card") || lower.contains("mifare")) {
            return "card";
        }
        if (lower.contains("finger")) {
            return "finger";
        }
        if ("isapi_punch".equals(lower) || "door_session".equals(lower)) {
            return "device";
        }
        return lower;
    }
}
