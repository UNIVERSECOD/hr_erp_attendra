package com.hic.service;

import com.hic.dto.PermissionDTO;
import com.hic.dto.PaginatedResponse;
import com.hic.exception.BadRequestException;
import com.hic.exception.ResourceNotFoundException;
import com.hic.model.Employee;
import com.hic.model.Leave;
import com.hic.model.PermissionType;
import com.hic.repository.EmployeeRepository;
import com.hic.repository.LeaveRepository;
import com.hic.repository.PermissionTypeRepository;
import com.hic.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.Locale;
import java.util.Set;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private static final Set<String> ALLOWED_STATUSES = Set.of("PENDING", "APPROVED", "REJECTED", "ACTIVE", "INACTIVE");

    private final LeaveRepository leaveRepository;
    private final PermissionTypeRepository permissionTypeRepository;
    private final EmployeeRepository employeeRepository;

    public PaginatedResponse<PermissionDTO> getAll(String search,
                                                   String status,
                                                   String type,
                                                   String start,
                                                   String end,
                                                   int page,
                                                   int size) {
        Long tenantId = requireTenant();
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1), Sort.by(Sort.Direction.DESC, "createdAt"));
        LocalDate startDate = parseDate(start, "start");
        LocalDate endDate = parseDate(end, "end");

        Page<Leave> leavePage = leaveRepository.searchByFilters(
                tenantId,
                trimToNull(search),
                normalizeStatus(status),
                trimToNull(type),
                startDate,
                endDate,
                pageable
        );
        List<PermissionDTO> content = leavePage.getContent().stream().map(this::toDTO).collect(Collectors.toList());
        return PaginatedResponse.of(content, leavePage.getTotalElements(), leavePage.getTotalPages(), leavePage.getNumber(), leavePage.getSize());
    }

    public PermissionDTO getById(Long id) {
        Long tenantId = requireTenant();
        Leave leave = findByIdAndTenant(id, tenantId);
        return toDTO(leave);
    }

    @Transactional
    public PermissionDTO create(PermissionDTO dto) {
        Long tenantId = requireTenant();
        Leave leave = toEntity(dto);
        leave.setTenantId(tenantId);
        leave.setCreatedBy(TenantContext.getUserId());
        leave.setStatus(dto.getStatus() == null || dto.getStatus().isBlank() ? "PENDING" : normalizeStatus(dto.getStatus()));
        validatePermission(leave);
        return toDTO(leaveRepository.save(leave));
    }

    @Transactional
    public PermissionDTO update(Long id, PermissionDTO dto) {
        Long tenantId = requireTenant();
        Leave leave = findByIdAndTenant(id, tenantId);

        if (dto.getName() != null && !dto.getName().isBlank()) {
            leave.setName(dto.getName());
        }
        leave.setDescription(dto.getDescription());
        leave.setLeaveType(resolveLeaveType(dto));
        leave.setApplyType(normalizeApplyType(dto.getApplyType()));
        leave.setTargetId(dto.getTargetId());
        leave.setStartDate(dto.getStartDate() != null ? LocalDate.parse(dto.getStartDate()) : leave.getStartDate());
        leave.setEndDate(dto.getEndDate() != null ? LocalDate.parse(dto.getEndDate()) : leave.getEndDate());
        leave.setReason(dto.getReason());

        if (dto.getStatus() != null && !dto.getStatus().isBlank()) {
            leave.setStatus(normalizeStatus(dto.getStatus()));
        }

        validatePermission(leave);
        return toDTO(leaveRepository.save(leave));
    }

    @Transactional
    public void delete(Long id) {
        Long tenantId = requireTenant();
        Leave leave = findByIdAndTenant(id, tenantId);
        leaveRepository.delete(leave);
    }

    @Transactional
    public PermissionDTO approve(Long id) {
        Long tenantId = requireTenant();
        Leave leave = findByIdAndTenant(id, tenantId);
        leave.setStatus("APPROVED");
        leave.setApprovedBy(TenantContext.getUserId());
        leave.setApprovalDate(LocalDateTime.now());
        return toDTO(leaveRepository.save(leave));
    }

    @Transactional
    public PermissionDTO reject(Long id) {
        Long tenantId = requireTenant();
        Leave leave = findByIdAndTenant(id, tenantId);
        leave.setStatus("REJECTED");
        leave.setApprovedBy(TenantContext.getUserId());
        leave.setApprovalDate(LocalDateTime.now());
        return toDTO(leaveRepository.save(leave));
    }

    public List<PermissionDTO> getEmployeeHistory(Long employeePk, Integer year) {
        Long tenantId = requireTenant();
        int targetYear = year == null ? Year.now().getValue() : year;
        LocalDate fromDate = LocalDate.of(targetYear, 1, 1);
        LocalDate toDate = LocalDate.of(targetYear, 12, 31);
        return leaveRepository.findEmployeeHistoryForPeriod(tenantId, employeePk, fromDate, toDate)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    private Leave findByIdAndTenant(Long id, Long tenantId) {
        return leaveRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Permission", id));
    }

    private PermissionDTO toDTO(Leave leave) {
        PermissionDTO dto = new PermissionDTO();
        dto.setId(leave.getId());
        dto.setTenantId(leave.getTenantId());
        dto.setName(leave.getName());
        dto.setDescription(leave.getDescription());
        dto.setLeaveType(leave.getLeaveType());
        dto.setApplyType(leave.getApplyType());
        dto.setTargetId(leave.getTargetId());
        dto.setStartDate(leave.getStartDate() != null ? leave.getStartDate().toString() : null);
        dto.setEndDate(leave.getEndDate() != null ? leave.getEndDate().toString() : null);
        dto.setStatus(leave.getStatus());
        dto.setReason(leave.getReason());
        dto.setCreatedBy(leave.getCreatedBy());
        dto.setCreatedAt(leave.getCreatedAt());
        dto.setUpdatedAt(leave.getUpdatedAt());
        dto.setApprovedBy(leave.getApprovedBy());
        dto.setApprovalDate(leave.getApprovalDate());
        enrichEmployeeInfo(dto);
        return dto;
    }

    private Leave toEntity(PermissionDTO dto) {
        Leave leave = new Leave();
        leave.setTenantId(dto.getTenantId());
        String leaveType = resolveLeaveType(dto);
        leave.setName(dto.getName() != null && !dto.getName().isBlank() ? dto.getName() : leaveType);
        leave.setDescription(dto.getDescription());
        leave.setLeaveType(leaveType);
        leave.setApplyType(normalizeApplyType(dto.getApplyType()));
        leave.setTargetId(dto.getTargetId());
        leave.setStartDate(dto.getStartDate() != null ? LocalDate.parse(dto.getStartDate()) : LocalDate.now());
        leave.setEndDate(dto.getEndDate() != null ? LocalDate.parse(dto.getEndDate()) : LocalDate.now());
        leave.setStatus(dto.getStatus() != null ? normalizeStatus(dto.getStatus()) : "PENDING");
        leave.setReason(dto.getReason());
        leave.setCreatedBy(dto.getCreatedBy());
        leave.setApprovedBy(dto.getApprovedBy());
        leave.setApprovalDate(dto.getApprovalDate());
        return leave;
    }

    private void validatePermission(Leave leave) {
        if (leave.getStartDate() == null || leave.getEndDate() == null) {
            throw new BadRequestException("Start and end dates are required");
        }
        if (leave.getEndDate().isBefore(leave.getStartDate())) {
            throw new BadRequestException("End date must be on or after start date");
        }
        if (!"COMPANY".equals(leave.getApplyType()) && leave.getTargetId() == null) {
            throw new BadRequestException("Target is required when applyType is not COMPANY");
        }
    }

    private String resolveLeaveType(PermissionDTO dto) {
        if (dto.getPermissionTypeId() != null) {
            PermissionType permissionType = permissionTypeRepository.findById(dto.getPermissionTypeId())
                    .orElseThrow(() -> new ResourceNotFoundException("PermissionType", dto.getPermissionTypeId()));
            Long tenantId = TenantContext.getTenantId();
            if (tenantId != null && !tenantId.equals(permissionType.getTenantId())) {
                throw new BadRequestException("PermissionType does not belong to current tenant");
            }
            return permissionType.getName();
        }
        return dto.getLeaveType() != null ? dto.getLeaveType() : "Medical Leave";
    }

    private void enrichEmployeeInfo(PermissionDTO dto) {
        if (!"EMPLOYEE".equals(dto.getApplyType()) || dto.getTargetId() == null) {
            return;
        }
        Long tenantId = dto.getTenantId();
        if (tenantId == null) {
            return;
        }
        Employee employee = employeeRepository.findByTenantIdAndId(tenantId, dto.getTargetId()).orElse(null);
        if (employee == null) {
            return;
        }
        dto.setEmployeeName(employee.getFirstName() + " " + employee.getLastName());
        dto.setEmployeeId(employee.getEmployeeId());
        dto.setFinNumber(employee.getFinNumber());
    }

    private String normalizeApplyType(String applyType) {
        String normalized = applyType == null || applyType.isBlank() ? "EMPLOYEE" : applyType.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("EMPLOYEE", "DEPARTMENT", "BRANCH", "GROUP", "COMPANY").contains(normalized)) {
            throw new BadRequestException("Invalid applyType");
        }
        return normalized;
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_STATUSES.contains(normalized)) {
            throw new BadRequestException("Invalid status");
        }
        return normalized;
    }

    private LocalDate parseDate(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (Exception ex) {
            throw new BadRequestException("Invalid " + fieldName + " date format");
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private Long requireTenant() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BadRequestException("Tenant context is required");
        }
        return tenantId;
    }
}
