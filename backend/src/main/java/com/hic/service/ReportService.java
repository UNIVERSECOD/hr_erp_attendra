package com.hic.service;

import com.hic.dto.ReportDTO;
import com.hic.model.DailyAttendanceSummary;
import com.hic.model.Department;
import com.hic.model.Employee;
import com.hic.repository.*;
import com.hic.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final DailyAttendanceSummaryRepository summaryRepository;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveTypeRepository leaveTypeRepository;

    public List<ReportDTO.AttendanceReportDTO> getAttendanceReport(LocalDate start, LocalDate end, Long branchId, Long departmentId) {
        List<Employee> employees = getFilteredEmployees(branchId, departmentId);
        List<ReportDTO.AttendanceReportDTO> result = new ArrayList<>();

        for (Employee emp : employees) {
            List<DailyAttendanceSummary> summaries = summaryRepository.findByEmployeeIdAndAttendanceDateBetween(emp.getId(), start, end);
            long presentDays = summaries.stream()
                    .filter(s -> s.getAttendanceStatus() == DailyAttendanceSummary.AttendanceStatus.PRESENT
                            || s.getAttendanceStatus() == DailyAttendanceSummary.AttendanceStatus.LATE
                            || s.getAttendanceStatus() == DailyAttendanceSummary.AttendanceStatus.WORKDAY_COMPLETE)
                    .count();
            long absentDays = summaries.stream().filter(s -> s.getAttendanceStatus() == DailyAttendanceSummary.AttendanceStatus.ABSENT).count();
            long lateDays = summaries.stream().filter(s -> s.getAttendanceStatus() == DailyAttendanceSummary.AttendanceStatus.LATE).count();
            double totalHours = summaries.stream().mapToDouble(s -> s.getHoursWorked() != null ? s.getHoursWorked() : 0.0).sum();

            ReportDTO.AttendanceReportDTO dto = new ReportDTO.AttendanceReportDTO();
            dto.setEmployeeId(emp.getId());
            dto.setEmployeeName(emp.getFirstName() + " " + emp.getLastName());
            dto.setPresentDays((int) presentDays);
            dto.setAbsentDays((int) absentDays);
            dto.setLateDays((int) lateDays);
            dto.setTotalHoursWorked(totalHours);
            result.add(dto);
        }
        return result;
    }

    public List<ReportDTO.LeaveReportDTO> getLeaveReport(LocalDate start, LocalDate end, Long branchId) {
        Long tenantId = TenantContext.getTenantId();
        List<Employee> employees = getFilteredEmployees(branchId, null);
        List<Long> empIds = employees.stream().map(Employee::getId).collect(Collectors.toList());
        if (empIds.isEmpty()) return List.of();

        List<com.hic.model.LeaveRequest> leaveRequests = tenantId != null
                ? leaveRequestRepository.findApprovedByTenantAndEmployeeIdsAndDateRange(tenantId, empIds, start, end)
                : leaveRequestRepository.findByEmployeeIdsAndDateRange(empIds, start, end);

        return leaveRequests.stream()
                .map(lr -> {
                    ReportDTO.LeaveReportDTO dto = new ReportDTO.LeaveReportDTO();
                    dto.setLeaveRequestId(lr.getId());
                    dto.setEmployeeId(lr.getEmployeeId());
                    employees.stream().filter(e -> e.getId().equals(lr.getEmployeeId())).findFirst()
                            .ifPresent(e -> dto.setEmployeeName(e.getFirstName() + " " + e.getLastName()));
                    leaveTypeRepository.findById(lr.getLeaveTypeId())
                            .ifPresent(lt -> dto.setLeaveTypeName(lt.getLeaveName()));
                    dto.setStartDate(lr.getStartDate());
                    dto.setEndDate(lr.getEndDate());
                    dto.setStatus(lr.getStatus() != null ? lr.getStatus().name() : null);
                    return dto;
                }).collect(Collectors.toList());
    }

    public List<ReportDTO.EmployeeSummaryDTO> getEmployeeSummary(Long branchId, Long departmentId) {
        List<Employee> employees = getFilteredEmployees(branchId, departmentId);
        return employees.stream().map(emp -> {
            ReportDTO.EmployeeSummaryDTO dto = new ReportDTO.EmployeeSummaryDTO();
            dto.setEmployeeId(emp.getId());
            dto.setEmployeeName(emp.getFirstName() + " " + emp.getLastName());
            dto.setEmploymentStatus(emp.getEmploymentStatus() != null ? emp.getEmploymentStatus().name() : null);
            if (emp.getDepartmentId() != null) {
                departmentRepository.findById(emp.getDepartmentId()).ifPresent(d -> dto.setDepartmentName(d.getDepartmentName()));
            }
            return dto;
        }).collect(Collectors.toList());
    }

    private List<Employee> getFilteredEmployees(Long branchId, Long departmentId) {
        Long tenantId = TenantContext.getTenantId();
        if (departmentId != null) {
            return tenantId != null
                    ? employeeRepository.findByTenantIdAndDepartmentId(tenantId, departmentId)
                    : employeeRepository.findByDepartmentId(departmentId);
        }
        if (branchId != null) {
            List<Department> depts = tenantId != null
                    ? departmentRepository.findByTenantIdAndBranchId(tenantId, branchId)
                    : departmentRepository.findByBranchId(branchId);
            List<Long> deptIds = depts.stream().map(Department::getId).collect(Collectors.toList());
            if (deptIds.isEmpty()) return new ArrayList<>();
            return tenantId != null
                    ? employeeRepository.findByTenantIdAndDepartmentIdIn(tenantId, deptIds,
                        org.springframework.data.domain.Pageable.unpaged()).getContent()
                    : employeeRepository.findByDepartmentIdIn(deptIds,
                        org.springframework.data.domain.Pageable.unpaged()).getContent();
        }
        if (tenantId != null) {
            return employeeRepository.findByTenantId(tenantId,
                    org.springframework.data.domain.Pageable.unpaged()).getContent();
        }
        return employeeRepository.findAll();
    }
}
