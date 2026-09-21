package com.hic.service;

import com.hic.dto.TabelMonthlyDTO;
import com.hic.model.DailyAttendanceSummary;
import com.hic.model.Employee;
import com.hic.model.EmployeePermission;
import com.hic.model.HolidayPermission;
import com.hic.model.LeaveRequest;
import com.hic.model.Position;
import com.hic.repository.DailyAttendanceSummaryRepository;
import com.hic.repository.EmployeePermissionRepository;
import com.hic.repository.EmployeeRepository;
import com.hic.repository.HolidayPermissionRepository;
import com.hic.repository.LeaveRequestRepository;
import com.hic.repository.PositionRepository;
import com.hic.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TabelService {

    private static final String QI_CODE = "Q/I";

    private final EmployeeRepository employeeRepository;
    private final PositionRepository positionRepository;
    private final DailyAttendanceSummaryRepository dailyAttendanceSummaryRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeePermissionRepository employeePermissionRepository;
    private final HolidayPermissionRepository holidayPermissionRepository;

    public TabelMonthlyDTO getMonthlyTabel(int year,
                                           int month,
                                           Long branchId,
                                           Long departmentId,
                                           Long positionId,
                                           String search) {
        Long tenantId = requireTenantId();

        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate start = yearMonth.atDay(1);
        LocalDate end = yearMonth.atEndOfMonth();

        List<Employee> employees = employeeRepository.findByTenantId(tenantId, Pageable.unpaged()).getContent()
                .stream()
                .filter(employee -> branchId == null || branchId.equals(employee.getBranchId()))
                .filter(employee -> departmentId == null || departmentId.equals(employee.getDepartmentId()))
                .filter(employee -> positionId == null || positionId.equals(employee.getPositionId()))
                .filter(employee -> matchesSearch(employee, search))
                .toList();

        Map<Long, String> positionNames = new HashMap<>();
        for (Position position : positionRepository.findByTenantId(tenantId)) {
            positionNames.put(position.getId(), position.getPositionName());
        }

        Map<Long, Set<LocalDate>> leaveByEmployee = buildLeaveDaysByEmployee(tenantId, employees, start, end);
        Map<Long, Set<LocalDate>> holidayByEmployee = buildHolidayDaysByEmployee(tenantId, employees, start, end);

        List<TabelMonthlyDTO.RowDTO> rows = new ArrayList<>();
        for (Employee employee : employees) {
            List<DailyAttendanceSummary> summaries = dailyAttendanceSummaryRepository
                    .findByEmployeeIdAndAttendanceDateBetween(employee.getId(), start, end);

            Map<LocalDate, DailyAttendanceSummary> summariesByDate = new HashMap<>();
            for (DailyAttendanceSummary summary : summaries) {
                summariesByDate.put(summary.getAttendanceDate(), summary);
            }

            LinkedHashMap<Integer, Object> daily = new LinkedHashMap<>();
            int workingDays = 0;
            double totalHours = 0.0;

            Set<LocalDate> leaveDates = leaveByEmployee.getOrDefault(employee.getId(), Set.of());
            Set<LocalDate> holidayDates = holidayByEmployee.getOrDefault(employee.getId(), Set.of());

            for (int day = 1; day <= yearMonth.lengthOfMonth(); day++) {
                LocalDate date = yearMonth.atDay(day);
                Object value;

                if (holidayDates.contains(date) || leaveDates.contains(date)) {
                    value = QI_CODE;
                } else {
                    DailyAttendanceSummary summary = summariesByDate.get(date);
                    double hours = summary != null && summary.getHoursWorked() != null ? summary.getHoursWorked() : 0.0;
                    if (hours > 0) {
                        double normalized = round2(hours);
                        value = normalized;
                        workingDays++;
                        totalHours += normalized;
                    } else if (isWeekend(date)) {
                        value = null;
                    } else {
                        value = 0;
                    }
                }

                daily.put(day, value);
            }

            TabelMonthlyDTO.RowDTO row = new TabelMonthlyDTO.RowDTO();
            row.setEmployeePk(employee.getId());
            row.setFin(employee.getFinNumber());
            row.setFullName(buildFullName(employee));
            row.setPosition(positionNames.getOrDefault(employee.getPositionId(), "-"));
            row.setDaily(daily);
            row.setWorkingDays(workingDays);
            row.setTotalHours(round2(totalHours));
            rows.add(row);
        }

        TabelMonthlyDTO dto = new TabelMonthlyDTO();
        dto.setYear(year);
        dto.setMonth(month);
        dto.setDaysInMonth(yearMonth.lengthOfMonth());
        dto.setEmployees(rows.size());
        dto.setRows(rows);
        return dto;
    }

    public byte[] exportMonthlyTabel(int year,
                                     int month,
                                     Long branchId,
                                     Long departmentId,
                                     Long positionId,
                                     String search) {
        TabelMonthlyDTO tabel = getMonthlyTabel(year, month, branchId, departmentId, positionId, search);

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Tabel");
            YearMonth yearMonth = YearMonth.of(year, month);

            Row titleRow = sheet.createRow(0);
            titleRow.createCell(0).setCellValue("Tabel arxivi: "
                    + yearMonth.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                    + " " + year);

            int column = 0;
            Row header = sheet.createRow(1);
            header.createCell(column++).setCellValue("s/s");
            header.createCell(column++).setCellValue("FIN kod");
            header.createCell(column++).setCellValue("Soyadı, adı, atasının adı");
            header.createCell(column++).setCellValue("Vəzifəsi");
            for (int day = 1; day <= tabel.getDaysInMonth(); day++) {
                header.createCell(column++).setCellValue(day);
            }
            header.createCell(column++).setCellValue("İş günlərinin sayı");
            header.createCell(column).setCellValue("İş saatlarının cəmi");

            int rowIndex = 2;
            int order = 1;
            for (TabelMonthlyDTO.RowDTO row : tabel.getRows()) {
                Row sheetRow = sheet.createRow(rowIndex++);
                int dataCol = 0;
                sheetRow.createCell(dataCol++).setCellValue(order++);
                sheetRow.createCell(dataCol++).setCellValue(row.getFin() == null ? "-" : row.getFin());
                sheetRow.createCell(dataCol++).setCellValue(row.getFullName());
                sheetRow.createCell(dataCol++).setCellValue(row.getPosition());

                for (int day = 1; day <= tabel.getDaysInMonth(); day++) {
                    Object value = row.getDaily().get(day);
                    if (value == null) {
                        sheetRow.createCell(dataCol++).setCellValue("");
                    } else if (value instanceof Number number) {
                        sheetRow.createCell(dataCol++).setCellValue(number.doubleValue());
                    } else {
                        sheetRow.createCell(dataCol++).setCellValue(String.valueOf(value));
                    }
                }

                sheetRow.createCell(dataCol++).setCellValue(row.getWorkingDays());
                sheetRow.createCell(dataCol).setCellValue(row.getTotalHours());
            }

            for (int i = 0; i <= 5; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(output);
            return output.toByteArray();
        } catch (Exception ex) {
            throw new RuntimeException("Failed to export tabel", ex);
        }
    }

    private Map<Long, Set<LocalDate>> buildLeaveDaysByEmployee(Long tenantId,
                                                                List<Employee> employees,
                                                                LocalDate start,
                                                                LocalDate end) {
        Map<Long, Set<LocalDate>> result = new HashMap<>();
        if (employees.isEmpty()) {
            return result;
        }

        List<Long> employeeIds = employees.stream().map(Employee::getId).toList();

        List<LeaveRequest> leaveRequests = leaveRequestRepository
                .findApprovedByTenantAndEmployeeIdsAndDateRange(tenantId, employeeIds, start, end);
        for (LeaveRequest leaveRequest : leaveRequests) {
            addDateRange(result, leaveRequest.getEmployeeId(), leaveRequest.getStartDate(), leaveRequest.getEndDate(), start, end);
        }

        List<EmployeePermission> employeePermissions = employeePermissionRepository.findByDateRange(tenantId, start, end);
        Set<Long> employeeIdSet = new HashSet<>(employeeIds);
        for (EmployeePermission permission : employeePermissions) {
            if (permission.getEmployeeId() == null || !employeeIdSet.contains(permission.getEmployeeId())) {
                continue;
            }
            String status = permission.getStatus() == null ? "" : permission.getStatus().name();
            if (!"ACTIVE".equals(status) && !"APPROVED".equals(status)) {
                continue;
            }
            addDateRange(result, permission.getEmployeeId(), permission.getStartDate(), permission.getEndDate(), start, end);
        }

        return result;
    }

    private Map<Long, Set<LocalDate>> buildHolidayDaysByEmployee(Long tenantId,
                                                                  List<Employee> employees,
                                                                  LocalDate start,
                                                                  LocalDate end) {
        Map<Long, Set<LocalDate>> result = new HashMap<>();
        if (employees.isEmpty()) {
            return result;
        }

        List<HolidayPermission> holidays = holidayPermissionRepository.findOverlapping(tenantId, start, end);
        for (HolidayPermission holiday : holidays) {
            String status = holiday.getStatus() == null ? "" : holiday.getStatus().trim().toUpperCase(Locale.ROOT);
            if (!"ACTIVE".equals(status)) {
                continue;
            }

            for (Employee employee : employees) {
                if (!isHolidayForEmployee(holiday, employee)) {
                    continue;
                }
                addDateRange(result, employee.getId(), holiday.getStartDate(), holiday.getEndDate(), start, end);
            }
        }

        return result;
    }

    private boolean isHolidayForEmployee(HolidayPermission holiday, Employee employee) {
        String scope = holiday.getApplyScope() == null
                ? "COMPANY"
                : holiday.getApplyScope().trim().toUpperCase(Locale.ROOT);

        return switch (scope) {
            case "COMPANY" -> true;
            case "BRANCH" -> contains(holiday.getTargetIds(), employee.getBranchId());
            case "DEPARTMENT" -> contains(holiday.getTargetIds(), employee.getDepartmentId());
            case "EMPLOYEE" -> contains(holiday.getEmployeeIds(), employee.getId());
            default -> false;
        };
    }

    private void addDateRange(Map<Long, Set<LocalDate>> map,
                              Long employeeId,
                              LocalDate start,
                              LocalDate end,
                              LocalDate clampStart,
                              LocalDate clampEnd) {
        if (employeeId == null || start == null || end == null) {
            return;
        }

        LocalDate from = start.isBefore(clampStart) ? clampStart : start;
        LocalDate to = end.isAfter(clampEnd) ? clampEnd : end;
        if (to.isBefore(from)) {
            return;
        }

        Set<LocalDate> dates = map.computeIfAbsent(employeeId, key -> new HashSet<>());
        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            dates.add(cursor);
            cursor = cursor.plusDays(1);
        }
    }

    private boolean matchesSearch(Employee employee, String search) {
        if (!StringUtils.hasText(search)) {
            return true;
        }

        String value = search.trim().toLowerCase(Locale.ROOT);
        String fullName = (employee.getLastName() + " " + employee.getFirstName() + " "
                + (employee.getFatherName() == null ? "" : employee.getFatherName())).toLowerCase(Locale.ROOT);

        return containsIgnoreCase(employee.getFinNumber(), value)
                || containsIgnoreCase(employee.getEmployeeId(), value)
                || fullName.contains(value);
    }

    private boolean containsIgnoreCase(String source, String search) {
        return source != null && source.toLowerCase(Locale.ROOT).contains(search);
    }

    private boolean contains(Long[] values, Long value) {
        if (values == null || value == null) {
            return false;
        }
        return Arrays.stream(values).anyMatch(value::equals);
    }

    private String buildFullName(Employee employee) {
        String fatherName = employee.getFatherName() == null ? "" : " " + employee.getFatherName();
        return employee.getLastName() + " " + employee.getFirstName() + fatherName;
    }

    private boolean isWeekend(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
    }

    private double round2(double value) {
        return Math.round(value * 100.0d) / 100.0d;
    }

    private Long requireTenantId() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("Tenant context is missing");
        }
        return tenantId;
    }
}
