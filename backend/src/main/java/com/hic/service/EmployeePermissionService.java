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
import com.hic.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EmployeePermissionService {

    private final EmployeePermissionRepository permissionRepository;
    private final EmployeeRepository employeeRepository;
    private final PermissionTypeRepository permissionTypeRepository;

    @Transactional
    public EmployeePermissionDTO grantPermission(Long employeeId,
                                                 Long permissionTypeId,
                                                 LocalDate startDate,
                                                 LocalDate endDate,
                                                 String reason,
                                                 EmployeePermission.Status status) {
        Long tenantId = requireTenant();
        validateDates(startDate, endDate);

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
        permission.setReason(reason);
        permission.setStatus(status == null ? EmployeePermission.Status.PENDING : status);

        if (permission.getStatus() == EmployeePermission.Status.APPROVED || permission.getStatus() == EmployeePermission.Status.ACTIVE) {
            permission.setApprovedBy(TenantContext.getUserId());
            permission.setApprovalDate(LocalDateTime.now());
        }

        return toDTO(permissionRepository.save(permission));
    }

    @Transactional
    public EmployeePermissionDTO updatePermission(Long id,
                                                  LocalDate startDate,
                                                  LocalDate endDate,
                                                  String reason,
                                                  EmployeePermission.Status status) {
        Long tenantId = requireTenant();
        validateDates(startDate, endDate);

        EmployeePermission permission = findByIdAndTenant(id, tenantId);
        permission.setStartDate(startDate);
        permission.setEndDate(endDate);
        permission.setReason(reason);

        if (status != null) {
            permission.setStatus(status);
            if (status == EmployeePermission.Status.APPROVED || status == EmployeePermission.Status.ACTIVE) {
                permission.setApprovedBy(TenantContext.getUserId());
                permission.setApprovalDate(LocalDateTime.now());
            }
        }

        return toDTO(permissionRepository.save(permission));
    }

    @Transactional
    public void revokePermission(Long id) {
        Long tenantId = requireTenant();
        EmployeePermission permission = findByIdAndTenant(id, tenantId);
        permission.setStatus(EmployeePermission.Status.INACTIVE);
        permissionRepository.save(permission);
    }

    public List<EmployeePermissionDTO> getAll() {
        Long tenantId = requireTenant();
        return permissionRepository.findByTenantId(tenantId).stream().map(this::toDTO).toList();
    }

    public List<EmployeePermissionDTO> getEmployeesWithPermission(Long permissionTypeId, LocalDate date) {
        Long tenantId = requireTenant();
        LocalDate targetDate = date != null ? date : LocalDate.now();
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
                                                           String reason,
                                                           EmployeePermission.Status status) {
        if (employeeIds == null || employeeIds.isEmpty()) {
            throw new BadRequestException("At least one employee is required");
        }

        List<EmployeePermissionDTO> results = new ArrayList<>();
        for (Long employeeId : employeeIds) {
            try {
                results.add(grantPermission(employeeId, permissionTypeId, startDate, endDate, reason, status));
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

    private void validateDates(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new BadRequestException("Start and end date are required");
        }
        if (endDate.isBefore(startDate)) {
            throw new BadRequestException("End date must be on or after start date");
        }
        if (endDate.isBefore(LocalDate.now())) {
            throw new BadRequestException("Cannot assign expired permission");
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
        dto.setReason(permission.getReason());
        dto.setStatus(permission.getStatus());
        dto.setApprovedBy(permission.getApprovedBy());
        dto.setApprovalDate(permission.getApprovalDate());
        dto.setCreatedAt(permission.getCreatedAt());
        dto.setUpdatedAt(permission.getUpdatedAt());
        return dto;
    }
}
