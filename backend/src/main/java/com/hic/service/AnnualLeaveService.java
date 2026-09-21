package com.hic.service;

import com.hic.dto.AnnualLeaveBalanceDTO;
import com.hic.exception.ResourceNotFoundException;
import com.hic.model.AnnualLeaveBalance;
import com.hic.model.Employee;
import com.hic.repository.AnnualLeaveBalanceRepository;
import com.hic.repository.EmployeeRepository;
import com.hic.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AnnualLeaveService {

    private final AnnualLeaveBalanceRepository annualLeaveBalanceRepository;
    private final EmployeeRepository employeeRepository;

    @Transactional(readOnly = true)
    public List<AnnualLeaveBalanceDTO> getAll(Integer year, Long employeeId) {
        Long tenantId = requireTenantId();
        Integer selectedYear = year != null ? year : Year.now().getValue();

        if (employeeId != null) {
            return annualLeaveBalanceRepository.findByTenantIdAndEmployeeIdAndYear(tenantId, employeeId, selectedYear)
                    .map(this::toDto)
                    .stream()
                    .toList();
        }

        return annualLeaveBalanceRepository.findByTenantIdAndYearOrderByEmployeeIdAsc(tenantId, selectedYear)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public AnnualLeaveBalanceDTO create(AnnualLeaveBalanceDTO dto) {
        validatePayload(dto, true);

        Long tenantId = requireTenantId();
        Employee employee = employeeRepository.findById(dto.getEmployeeId())
                .filter(item -> tenantId.equals(item.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));

        annualLeaveBalanceRepository.findByTenantIdAndEmployeeIdAndYear(tenantId, dto.getEmployeeId(), dto.getYear())
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Annual leave already exists for this employee and year");
                });

        AnnualLeaveBalance entity = new AnnualLeaveBalance();
        entity.setTenantId(tenantId);
        entity.setEmployeeId(employee.getId());
        entity.setYear(dto.getYear());
        entity.setEntitlementDays(zeroIfNull(dto.getEntitlementDays()));
        entity.setUsedDays(zeroIfNull(dto.getUsedDays()));
        entity.setCarryoverDays(zeroIfNull(dto.getCarryoverDays()));
        entity.setStatus(normalizeStatus(dto.getStatus()));

        recomputeRemaining(entity);
        validateBusinessRules(entity);

        return toDto(annualLeaveBalanceRepository.save(entity));
    }

    @Transactional
    public AnnualLeaveBalanceDTO update(Long id, AnnualLeaveBalanceDTO dto) {
        Long tenantId = requireTenantId();

        AnnualLeaveBalance entity = annualLeaveBalanceRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Annual leave balance not found"));

        if (dto.getEntitlementDays() != null) {
            entity.setEntitlementDays(dto.getEntitlementDays());
        }
        if (dto.getUsedDays() != null) {
            entity.setUsedDays(dto.getUsedDays());
        }
        if (dto.getCarryoverDays() != null) {
            entity.setCarryoverDays(dto.getCarryoverDays());
        }
        if (dto.getStatus() != null) {
            entity.setStatus(normalizeStatus(dto.getStatus()));
        }

        recomputeRemaining(entity);
        validateBusinessRules(entity);

        return toDto(annualLeaveBalanceRepository.save(entity));
    }

    @Transactional
    public AnnualLeaveBalanceDTO recalculate(Long employeeId, Integer year) {
        if (employeeId == null || year == null) {
            throw new IllegalArgumentException("employeeId and year are required");
        }

        Long tenantId = requireTenantId();
        AnnualLeaveBalance entity = annualLeaveBalanceRepository.findByTenantIdAndEmployeeIdAndYear(tenantId, employeeId, year)
                .orElseThrow(() -> new ResourceNotFoundException("Annual leave balance not found"));

        recomputeRemaining(entity);
        validateBusinessRules(entity);

        return toDto(annualLeaveBalanceRepository.save(entity));
    }

    private AnnualLeaveBalanceDTO toDto(AnnualLeaveBalance entity) {
        AnnualLeaveBalanceDTO dto = new AnnualLeaveBalanceDTO();
        dto.setId(entity.getId());
        dto.setEmployeeId(entity.getEmployeeId());
        dto.setEmployeeName(resolveEmployeeName(entity.getTenantId(), entity.getEmployeeId()));
        dto.setYear(entity.getYear());
        dto.setEntitlementDays(entity.getEntitlementDays());
        dto.setUsedDays(entity.getUsedDays());
        dto.setRemainingDays(entity.getRemainingDays());
        dto.setCarryoverDays(entity.getCarryoverDays());
        dto.setStatus(normalizeStatus(entity.getStatus()));
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    private String resolveEmployeeName(Long tenantId, Long employeeId) {
        return employeeRepository.findById(employeeId)
                .filter(item -> tenantId.equals(item.getTenantId()))
                .map(employee -> (employee.getFirstName() + " " + employee.getLastName()).trim())
                .orElse(null);
    }

    private void validatePayload(AnnualLeaveBalanceDTO dto, boolean requireEmployeeAndYear) {
        if (dto == null) {
            throw new IllegalArgumentException("Payload is required");
        }
        if (requireEmployeeAndYear) {
            if (dto.getEmployeeId() == null) {
                throw new IllegalArgumentException("employeeId is required");
            }
            if (dto.getYear() == null) {
                throw new IllegalArgumentException("year is required");
            }
        }
    }

    private void validateBusinessRules(AnnualLeaveBalance entity) {
        int entitlement = zeroIfNull(entity.getEntitlementDays());
        int used = zeroIfNull(entity.getUsedDays());
        int carryover = zeroIfNull(entity.getCarryoverDays());

        if (entitlement < 0 || used < 0 || carryover < 0) {
            throw new IllegalArgumentException("Days cannot be negative");
        }

        if (used > entitlement + carryover) {
            throw new IllegalArgumentException("usedDays cannot exceed entitlement + carryover");
        }
    }

    private void recomputeRemaining(AnnualLeaveBalance entity) {
        int remaining = zeroIfNull(entity.getEntitlementDays())
                + zeroIfNull(entity.getCarryoverDays())
                - zeroIfNull(entity.getUsedDays());
        entity.setRemainingDays(Math.max(remaining, 0));
    }

    private int zeroIfNull(Integer value) {
        return value == null ? 0 : value;
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "ACTIVE";
        }
        return status.trim().toUpperCase(Locale.ROOT);
    }

    private Long requireTenantId() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("Tenant context is missing");
        }
        return tenantId;
    }
}
