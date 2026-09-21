package com.hic.service;

import com.hic.dto.HolidayPermissionDTO;
import com.hic.exception.ResourceNotFoundException;
import com.hic.model.HolidayPermission;
import com.hic.repository.HolidayPermissionRepository;
import com.hic.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class HolidayPermissionService {

    private static final Set<String> VALID_SCOPES = Set.of("COMPANY", "DEPARTMENT", "BRANCH", "EMPLOYEE");

    private final HolidayPermissionRepository holidayPermissionRepository;

    @Transactional(readOnly = true)
    public List<HolidayPermissionDTO> getAll() {
        Long tenantId = requireTenantId();
        return holidayPermissionRepository.findByTenantIdOrderByStartDateDesc(tenantId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public HolidayPermissionDTO getById(Long id) {
        Long tenantId = requireTenantId();
        HolidayPermission entity = holidayPermissionRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Holiday permission not found"));
        return toDto(entity);
    }

    @Transactional
    public HolidayPermissionDTO create(HolidayPermissionDTO dto) {
        validateDates(dto.getStartDate(), dto.getEndDate());
        String scope = normalizeScope(dto.getApplyScope());
        List<Long> targetIds = normalizeIdList(dto.getTargetIds());
        List<Long> employeeIds = normalizeIdList(dto.getEmployeeIds());
        validateScopeTargets(scope, targetIds, employeeIds);

        Long tenantId = requireTenantId();
        ensureNoOverlap(null, tenantId, scope, targetIds, employeeIds, dto.getStartDate(), dto.getEndDate());

        HolidayPermission entity = new HolidayPermission();
        entity.setTenantId(tenantId);
        entity.setName(requireName(dto.getName()));
        entity.setDescription(blankToNull(dto.getDescription()));
        entity.setStartDate(dto.getStartDate());
        entity.setEndDate(dto.getEndDate());
        entity.setApplyScope(scope);
        entity.setTargetIds(targetIds.toArray(new Long[0]));
        entity.setEmployeeIds(employeeIds.toArray(new Long[0]));
        entity.setStatus(normalizeStatus(dto.getStatus()));
        entity.setCreatedBy(TenantContext.getUserId());

        return toDto(holidayPermissionRepository.save(entity));
    }

    @Transactional
    public HolidayPermissionDTO update(Long id, HolidayPermissionDTO dto) {
        Long tenantId = requireTenantId();
        HolidayPermission entity = holidayPermissionRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Holiday permission not found"));

        validateDates(dto.getStartDate(), dto.getEndDate());
        String scope = normalizeScope(dto.getApplyScope());
        List<Long> targetIds = normalizeIdList(dto.getTargetIds());
        List<Long> employeeIds = normalizeIdList(dto.getEmployeeIds());
        validateScopeTargets(scope, targetIds, employeeIds);
        ensureNoOverlap(id, tenantId, scope, targetIds, employeeIds, dto.getStartDate(), dto.getEndDate());

        entity.setName(requireName(dto.getName()));
        entity.setDescription(blankToNull(dto.getDescription()));
        entity.setStartDate(dto.getStartDate());
        entity.setEndDate(dto.getEndDate());
        entity.setApplyScope(scope);
        entity.setTargetIds(targetIds.toArray(new Long[0]));
        entity.setEmployeeIds(employeeIds.toArray(new Long[0]));
        entity.setStatus(normalizeStatus(dto.getStatus()));

        return toDto(holidayPermissionRepository.save(entity));
    }

    @Transactional
    public void delete(Long id) {
        Long tenantId = requireTenantId();
        HolidayPermission entity = holidayPermissionRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Holiday permission not found"));
        holidayPermissionRepository.delete(entity);
    }

    @Transactional(readOnly = true)
    public List<HolidayPermissionDTO> getRange(LocalDate start, LocalDate end) {
        validateDates(start, end);
        Long tenantId = requireTenantId();
        return holidayPermissionRepository.findOverlapping(tenantId, start, end)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean isHoliday(LocalDate date, Long employeeId, Long deptId, Long branchId) {
        if (date == null) {
            return false;
        }

        Long tenantId = requireTenantId();
        List<HolidayPermission> candidates = holidayPermissionRepository.findOverlapping(tenantId, date, date);

        for (HolidayPermission permission : candidates) {
            if (!"ACTIVE".equals(normalizeStatus(permission.getStatus()))) {
                continue;
            }
            String scope = normalizeScope(permission.getApplyScope());
            if ("COMPANY".equals(scope)) {
                return true;
            }
            if ("DEPARTMENT".equals(scope) && deptId != null && contains(permission.getTargetIds(), deptId)) {
                return true;
            }
            if ("BRANCH".equals(scope) && branchId != null && contains(permission.getTargetIds(), branchId)) {
                return true;
            }
            if ("EMPLOYEE".equals(scope) && employeeId != null && contains(permission.getEmployeeIds(), employeeId)) {
                return true;
            }
        }

        return false;
    }

    private HolidayPermissionDTO toDto(HolidayPermission entity) {
        HolidayPermissionDTO dto = new HolidayPermissionDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setStartDate(entity.getStartDate());
        dto.setEndDate(entity.getEndDate());
        dto.setApplyScope(normalizeScope(entity.getApplyScope()));
        dto.setTargetIds(arrayToList(entity.getTargetIds()));
        dto.setEmployeeIds(arrayToList(entity.getEmployeeIds()));
        dto.setStatus(normalizeStatus(entity.getStatus()));
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    private void ensureNoOverlap(Long currentId,
                                 Long tenantId,
                                 String scope,
                                 List<Long> targetIds,
                                 List<Long> employeeIds,
                                 LocalDate start,
                                 LocalDate end) {
        List<HolidayPermission> overlapping = holidayPermissionRepository.findOverlapping(tenantId, start, end);
        for (HolidayPermission existing : overlapping) {
            if (currentId != null && currentId.equals(existing.getId())) {
                continue;
            }
            if (!"ACTIVE".equals(normalizeStatus(existing.getStatus()))) {
                continue;
            }
            if (!scope.equals(normalizeScope(existing.getApplyScope()))) {
                continue;
            }

            boolean conflict;
            switch (scope) {
                case "COMPANY" -> conflict = true;
                case "DEPARTMENT", "BRANCH" -> conflict = intersects(targetIds, arrayToList(existing.getTargetIds()));
                case "EMPLOYEE" -> conflict = intersects(employeeIds, arrayToList(existing.getEmployeeIds()));
                default -> conflict = false;
            }

            if (conflict) {
                throw new IllegalArgumentException("Overlapping holiday permission exists for selected scope/targets");
            }
        }
    }

    private void validateDates(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("startDate and endDate are required");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate cannot be before startDate");
        }
    }

    private void validateScopeTargets(String scope, List<Long> targetIds, List<Long> employeeIds) {
        if (("DEPARTMENT".equals(scope) || "BRANCH".equals(scope)) && targetIds.isEmpty()) {
            throw new IllegalArgumentException("targetIds are required for selected scope");
        }
        if ("EMPLOYEE".equals(scope) && employeeIds.isEmpty()) {
            throw new IllegalArgumentException("employeeIds are required for EMPLOYEE scope");
        }
    }

    private String requireName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        return value.trim();
    }

    private String normalizeScope(String scope) {
        String normalized = scope == null ? "COMPANY" : scope.trim().toUpperCase(Locale.ROOT);
        if (!VALID_SCOPES.contains(normalized)) {
            throw new IllegalArgumentException("Invalid applyScope");
        }
        return normalized;
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "ACTIVE";
        }
        return status.trim().toUpperCase(Locale.ROOT);
    }

    private List<Long> normalizeIdList(List<Long> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private List<Long> arrayToList(Long[] values) {
        if (values == null || values.length == 0) {
            return List.of();
        }
        return Arrays.stream(values)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private boolean contains(Long[] values, Long target) {
        if (values == null || target == null) {
            return false;
        }
        for (Long value : values) {
            if (target.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private boolean intersects(List<Long> left, List<Long> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return false;
        }
        Set<Long> rightSet = new HashSet<>(right);
        for (Long value : left) {
            if (rightSet.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private Long requireTenantId() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("Tenant context is missing");
        }
        return tenantId;
    }
}
