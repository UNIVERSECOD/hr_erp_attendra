package com.hic.service;

import com.hic.model.AttendanceLog;
import com.hic.model.AttendanceRecord;
import com.hic.model.Employee;
import com.hic.repository.AttendanceLogRepository;
import com.hic.repository.AttendanceRecordRepository;
import com.hic.repository.EmployeeRepository;
import com.hic.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AttendanceCalculationService {
    private final AttendanceLogRepository attendanceLogRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final EmployeeRepository employeeRepository;
    private final HolidayService holidayService;
    private final LeaveService leaveService;
    private final AttendanceInferenceService attendanceInferenceService;
    private final AttendanceScheduleResolver attendanceScheduleResolver;
    private final AttendanceTimeCalculator attendanceTimeCalculator;

    @Transactional
    public AttendanceRecord calculateForDay(Long employeeId, LocalDate workDate) {
        Employee employee = employeeRepository.findById(employeeId).orElse(null);
        AttendanceRecord record = attendanceRecordRepository.findByEmployeeIdAndWorkDate(employeeId, workDate)
                .orElseGet(AttendanceRecord::new);

        record.setEmployeeId(employeeId);
        record.setWorkDate(workDate);
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null && employee != null) {
            tenantId = employee.getTenantId();
        }
        if (tenantId == null) {
            tenantId = record.getTenantId();
        }
        if (tenantId == null) {
            throw new IllegalStateException("Tenant context is required for attendance calculation");
        }
        record.setTenantId(tenantId);

        AttendanceScheduleResolver.DaySchedule schedule = attendanceScheduleResolver.resolve(employee, workDate);
        record.setShiftType(schedule.shiftType());
        record.setTimetableId(schedule.timetableId());

        List<AttendanceLog> logs = findDayLogs(employeeId, workDate);
        AttendanceInferenceService.AttendanceInference inference = attendanceInferenceService.inferDay(logs, workDate);
        LocalDateTime firstEntry = inference.firstEntry();
        LocalDateTime lastExit = inference.lastExit();
        record.setEntryTime(firstEntry);
        record.setExitTime(lastExit);

        AttendanceTimeCalculator.Calculation calculation = attendanceTimeCalculator.calculate(
                workDate, inference, schedule);
        record.setWorkedMinutes(calculation.workedMinutes());
        record.setOvertimeMinutes(calculation.overtimeMinutes());
        record.setLateMinutes(calculation.lateMinutes());
        record.setEarlyLeaveMinutes(calculation.earlyLeaveMinutes());

        if (leaveService.hasActiveLeave(employeeId, workDate)) {
            record.setStatus("ON_LEAVE");
        } else if (holidayService.isHoliday(workDate)) {
            record.setStatus("HOLIDAY");
        } else {
            record.setStatus(calculation.status().name());
        }

        return attendanceRecordRepository.save(record);
    }

    public LocalDateTime getFirstEntry(Long employeeId, LocalDate workDate) {
        return attendanceInferenceService.inferDay(findDayLogs(employeeId, workDate), workDate).firstEntry();
    }

    public LocalDateTime getLastExit(Long employeeId, LocalDate workDate) {
        return attendanceInferenceService.inferDay(findDayLogs(employeeId, workDate), workDate).lastExit();
    }

    @Transactional
    public void recalculate(LocalDate start, LocalDate end, Long employeeId) {
        List<Employee> employees;
        Long tenantId = TenantContext.getTenantId();
        if (employeeId != null) {
            employees = employeeRepository.findById(employeeId).map(List::of).orElse(List.of());
        } else if (tenantId != null) {
            employees = employeeRepository.findByTenantId(tenantId, org.springframework.data.domain.Pageable.unpaged()).getContent();
        } else {
            throw new IllegalStateException("Tenant context is required to recalculate attendance for all employees");
        }

        for (Employee employee : employees) {
            LocalDate date = start;
            while (!date.isAfter(end)) {
                calculateForDay(employee.getId(), date);
                date = date.plusDays(1);
            }
        }
    }

    private List<AttendanceLog> findDayLogs(Long employeeId, LocalDate workDate) {
        List<AttendanceLog> candidates = attendanceLogRepository.findByEmployeeIdAndCheckInTimeBetween(
                employeeId,
                workDate.atStartOfDay(),
                workDate.plusDays(1).atStartOfDay().minusNanos(1)
        );
        return candidates.stream()
                .filter(log -> attendanceInferenceService.belongsToWorkDate(log, workDate))
                .toList();
    }
}
