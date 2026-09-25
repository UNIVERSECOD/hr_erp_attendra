package com.hic.service;

import com.hic.dto.EmployeePermissionDTO;
import com.hic.exception.BadRequestException;
import com.hic.exception.ResourceNotFoundException;
import com.hic.model.Employee;
import com.hic.model.EmployeePermission;
import com.hic.model.PermissionType;
import com.hic.repository.EmployeePermissionRepository;
import com.hic.repository.EmployeeRepository;
import com.hic.repository.PermissionTypeRepository;
import com.hic.util.AppTimeZone;
import com.hic.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EmployeePermissionService {

    private final EmployeePermissionRepository permissionRepository;
    private final EmployeeRepository employeeRepository;
    private final PermissionTypeRepository permissionTypeRepository;
    private final AttendanceCalculationService attendanceCalculationService;
    private final AttendanceService attendanceService;

    @Transactional
    public EmployeePermissionDTO grantPermission(Long employeeId,
                                                 Long permissionTypeId,
                                                 LocalDate startDate,
                                                 LocalDate endDate,
                                                 LocalTime startTime,
                                                 LocalTime endTime,
                                                 Boolean deductFromWorkHours,
                                                 String reason,
                                                 EmployeePermission.Status status) {
        Long tenantId = requireTenant();
        validatePeriod(startDate, endDate, startTime, endTime, reason);

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", employeeId));
        PermissionType permissionType = permissionTypeRepository.findById(permissionTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("PermissionType", permissionTypeId));

        ensureSameTenant(tenantId, employee.getTenantId(), "Employee");
        ensureSameTenant(tenantId, permissionType.getTenantId(), "PermissionType");

        if (requiresReason(permissionType.getCode()) && (reason == null || reason.isBlank())) {
            throw new BadRequestException("Reason is required for selected permission type");
        }

        EmployeePermission permission = new EmployeePermission();
        permission.setTenantId(tenantId);
        permission.setEmployeeId(employeeId);
        permission.setPermissionTypeId(permissionTypeId);
        permission.setStartDate(startDate);
        permission.setEndDate(endDate);
        permission.setStartTime(startTime);
        permission.setEndTime(endTime);
        permission.setDeductFromWorkHours(deductFromWorkHours == null || deductFromWorkHours);
        permission.setReason(trimToNull(reason));
        permission.setStatus(status == null ? EmployeePermission.Status.PENDING : status);

        if (permission.getStatus() == EmployeePermission.Status.APPROVED || permission.getStatus() == EmployeePermission.Status.ACTIVE) {
            permission.setApprovedBy(TenantContext.getUserId());
            permission.setApprovalDate(AppTimeZone.now());
        }

        EmployeePermission saved = permissionRepository.save(permission);
        recalculateAttendance(employeeId, startDate, endDate);
        return toDTO(saved);
    }

    @Transactional
    public EmployeePermissionDTO updatePermission(Long id,
                                                  LocalDate startDate,
                                                  LocalDate endDate,
                                                  LocalTime startTime,
                                                  LocalTime endTime,
                                                  Boolean deductFromWorkHours,
                                                  String reason,
                                                  EmployeePermission.Status status) {
        Long tenantId = requireTenant();
        validatePeriod(startDate, endDate, startTime, endTime, reason);

        EmployeePermission permission = findByIdAndTenant(id, tenantId);
        LocalDate previousStart = permission.getStartDate();
        LocalDate previousEnd = permission.getEndDate();
        permission.setStartDate(startDate);
        permission.setEndDate(endDate);
        permission.setStartTime(startTime);
        permission.setEndTime(endTime);
        permission.setDeductFromWorkHours(deductFromWorkHours == null || deductFromWorkHours);
        permission.setReason(trimToNull(reason));

        if (status != null) {
            permission.setStatus(status);
            if (status == EmployeePermission.Status.APPROVED || status == EmployeePermission.Status.ACTIVE) {
                permission.setApprovedBy(TenantContext.getUserId());
                permission.setApprovalDate(AppTimeZone.now());
            }
        }

        EmployeePermission saved = permissionRepository.save(permission);
        LocalDate recalculateFrom = previousStart.isBefore(startDate) ? previousStart : startDate;
        LocalDate recalculateTo = previousEnd.isAfter(endDate) ? previousEnd : endDate;
        recalculateAttendance(permission.getEmployeeId(), recalculateFrom, recalculateTo);
        return toDTO(saved);
    }

    @Transactional
    public void revokePermission(Long id) {
        Long tenantId = requireTenant();
        EmployeePermission permission = findByIdAndTenant(id, tenantId);
        permission.setStatus(EmployeePermission.Status.INACTIVE);
        permissionRepository.save(permission);
        recalculateAttendance(permission.getEmployeeId(), permission.getStartDate(), permission.getEndDate());
    }

    public List<EmployeePermissionDTO> getAll() {
        Long tenantId = requireTenant();
        return permissionRepository.findByTenantId(tenantId).stream().map(this::toDTO).toList();
    }

    public List<EmployeePermissionDTO> getEmployeesWithPermission(Long permissionTypeId, LocalDate date) {
        Long tenantId = requireTenant();
        LocalDate targetDate = date != null ? date : AppTimeZone.today();
        return permissionRepository.findEmployeesWithPermission(tenantId, permissionTypeId, targetDate)
                .stream().map(this::toDTO).toList();
    }

    public List<EmployeePermissionDTO> getPermissionHistory(Long employeeId) {
        Long tenantId = requireTenant();
        return permissionRepository.findByTenantIdAndEmployeeIdOrderByStartDateDesc(tenantId, employeeId)
                .stream().map(this::toDTO).toList();
    }

    @Transactional
    public List<EmployeePermissionDTO> bulkGrantPermission(List<Long> employeeIds,
                                                           Long permissionTypeId,
                                                           LocalDate startDate,
                                                           LocalDate endDate,
                                                           LocalTime startTime,
                                                           LocalTime endTime,
                                                           Boolean deductFromWorkHours,
                                                           String reason,
                                                           EmployeePermission.Status status) {
        if (employeeIds == null || employeeIds.isEmpty()) {
            throw new BadRequestException("At least one employee is required");
        }

        List<EmployeePermissionDTO> results = new ArrayList<>();
        for (Long employeeId : employeeIds) {
            try {
                results.add(grantPermission(
                        employeeId, permissionTypeId, startDate, endDate,
                        startTime, endTime, deductFromWorkHours, reason, status));
            } catch (RuntimeException ignored) {
                // continue processing remaining employees
            }
        }
        if (results.isEmpty()) {
            throw new BadRequestException("No permission could be granted in bulk operation");
        }
        return results;
    }

    private EmployeePermission findByIdAndTenant(Long id, Long tenantId) {
        EmployeePermission permission = permissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("EmployeePermission", id));
        ensureSameTenant(tenantId, permission.getTenantId(), "EmployeePermission");
        return permission;
    }

    private void validatePeriod(
            LocalDate startDate,
            LocalDate endDate,
            LocalTime startTime,
            LocalTime endTime,
            String reason
    ) {
        if (startDate == null || endDate == null) {
            throw new BadRequestException("Start and end date are required");
        }
        if (endDate.isBefore(startDate)) {
            throw new BadRequestException("End date must be on or after start date");
        }
        if (endDate.isBefore(AppTimeZone.today())) {
            throw new BadRequestException("Cannot assign expired permission");
        }
        if ((startTime == null) != (endTime == null)) {
            throw new BadRequestException("Start and end time must both be provided");
        }
        if (startTime != null && endTime.equals(startTime)) {
            throw new BadRequestException("Start and end time must be different");
        }
        if (startTime != null && (reason == null || reason.isBlank())) {
            throw new BadRequestException("Reason is required for hourly permission");
        }
    }

    private boolean requiresReason(String permissionCode) {
        return "MEDICAL_LEAVE".equalsIgnoreCase(permissionCode)
                || "PREGNANCY_LEAVE".equalsIgnoreCase(permissionCode)
                || "MATERNITY_LEAVE".equalsIgnoreCase(permissionCode);
    }

    private Long requireTenant() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BadRequestException("Tenant context is required");
        }
        return tenantId;
    }

    private void ensureSameTenant(Long expectedTenantId, Long actualTenantId, String resourceName) {
        if (!expectedTenantId.equals(actualTenantId)) {
            throw new BadRequestException(resourceName + " does not belong to current tenant");
        }
    }

    private EmployeePermissionDTO toDTO(EmployeePermission permission) {
        EmployeePermissionDTO dto = new EmployeePermissionDTO();
        dto.setId(permission.getId());
        dto.setTenantId(permission.getTenantId());
        dto.setEmployeeId(permission.getEmployeeId());
        dto.setPermissionTypeId(permission.getPermissionTypeId());
        dto.setStartDate(permission.getStartDate());
        dto.setEndDate(permission.getEndDate());
        dto.setStartTime(permission.getStartTime());
        dto.setEndTime(permission.getEndTime());
        dto.setDeductFromWorkHours(permission.getDeductFromWorkHours());
        dto.setReason(permission.getReason());
        dto.setStatus(permission.getStatus());
        dto.setApprovedBy(permission.getApprovedBy());
        dto.setApprovalDate(permission.getApprovalDate());
        dto.setCreatedAt(permission.getCreatedAt());
        dto.setUpdatedAt(permission.getUpdatedAt());
        return dto;
    }

    private void recalculateAttendance(Long employeeId, LocalDate startDate, LocalDate endDate) {
        LocalDate lastDate = endDate.isAfter(AppTimeZone.today()) ? AppTimeZone.today() : endDate;
        if (startDate.isAfter(lastDate)) {
            return;
        }
        for (LocalDate date = startDate; !date.isAfter(lastDate); date = date.plusDays(1)) {
            attendanceCalculationService.calculateForDay(employeeId, date);
            attendanceService.generateDailySummary(employeeId, date);
        }
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
