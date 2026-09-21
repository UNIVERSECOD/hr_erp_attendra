package com.hic.service;

import com.hic.dto.DepartmentDTO;
import com.hic.exception.ResourceNotFoundException;
import com.hic.model.Branch;
import com.hic.model.Department;
import com.hic.repository.BranchRepository;
import com.hic.repository.DepartmentRepository;
import com.hic.repository.EmployeeRepository;
import com.hic.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final BranchRepository branchRepository;
    private final EmployeeRepository employeeRepository;
    private final UserScopeService userScopeService;

    public List<DepartmentDTO> getAll(Long requestedBranchId) {
        Long tenantId = TenantContext.getTenantId();
        Long effectiveBranchId = userScopeService.resolveBranchScope(requestedBranchId);
        List<Department> departments;
        if (tenantId != null && effectiveBranchId != null) {
            departments = departmentRepository.findByTenantIdAndBranchId(tenantId, effectiveBranchId);
        } else if (tenantId != null) {
            departments = departmentRepository.findByTenantId(tenantId);
        } else if (effectiveBranchId != null) {
            departments = departmentRepository.findByBranchId(effectiveBranchId);
        } else {
            departments = departmentRepository.findAll();
        }
        return departments.stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<DepartmentDTO> getByBranch(Long branchId) {
        return departmentRepository.findByBranchId(branchId).stream().map(this::toDTO).collect(Collectors.toList());
    }

    public DepartmentDTO getById(Long id) {
        return toDTO(departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department", id)));
    }

    @Transactional
    public DepartmentDTO create(DepartmentDTO dto) {
        Long tenantId = TenantContext.getTenantId();
        // branchId is optional now, use head office if not provided
        if (dto.getBranchId() != null && !branchRepository.existsById(dto.getBranchId())) {
            throw new ResourceNotFoundException("Branch", dto.getBranchId());
        }
        Department dept = new Department();
        dept.setDepartmentName(dto.getDepartmentName());
        dept.setDescription(dto.getDescription());
        dept.setParentDepartmentId(dto.getParentDepartmentId());
        dept.setCalculateOvertime(dto.getCalculateOvertime() != null ? dto.getCalculateOvertime() : false);
        dept.setFlexShift(dto.getFlexShift() != null ? dto.getFlexShift() : false);
        dept.setTimetable(dto.getTimetable());
        dept.setTimetableId(dto.getTimetableId());
        if (dto.getBranchId() != null) {
            dept.setBranchId(dto.getBranchId());
        } else {
            // default to head office branch
            List<Branch> tenantBranches = tenantId != null
                    ? branchRepository.findByTenantId(tenantId)
                    : branchRepository.findAll();
            tenantBranches.stream()
                    .filter(branch -> Boolean.TRUE.equals(branch.getIsHeadOffice()))
                    .findFirst()
                    .or(() -> tenantBranches.stream().findFirst())
                    .ifPresent(branch -> dept.setBranchId(branch.getId()));
        }
        if (tenantId != null) dept.setTenantId(tenantId);
        return toDTO(departmentRepository.save(dept));
    }

    @Transactional
    public DepartmentDTO update(Long id, DepartmentDTO dto) {
        Department dept = departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department", id));
        dept.setDepartmentName(dto.getDepartmentName());
        dept.setDescription(dto.getDescription());
        dept.setParentDepartmentId(dto.getParentDepartmentId());
        dept.setCalculateOvertime(dto.getCalculateOvertime() != null ? dto.getCalculateOvertime() : false);
        dept.setFlexShift(dto.getFlexShift() != null ? dto.getFlexShift() : false);
        dept.setTimetable(dto.getTimetable());
        dept.setTimetableId(dto.getTimetableId());
        if (dto.getBranchId() != null) dept.setBranchId(dto.getBranchId());
        return toDTO(departmentRepository.save(dept));
    }

    @Transactional
    public void delete(Long id) {
        if (!departmentRepository.existsById(id)) {
            throw new ResourceNotFoundException("Department", id);
        }
        departmentRepository.deleteById(id);
    }

    private DepartmentDTO toDTO(Department dept) {
        DepartmentDTO dto = new DepartmentDTO();
        dto.setId(dept.getId());
        dto.setDepartmentName(dept.getDepartmentName());
        dto.setDescription(dept.getDescription());
        dto.setBranchId(dept.getBranchId());
        dto.setParentDepartmentId(dept.getParentDepartmentId());
        dto.setCreatedAt(dept.getCreatedAt());
        dto.setCalculateOvertime(dept.getCalculateOvertime());
        dto.setFlexShift(dept.getFlexShift());
        dto.setTimetable(dept.getTimetable());
        dto.setTimetableId(dept.getTimetableId());

        // Resolve parent name
        if (dept.getParentDepartmentId() != null) {
            departmentRepository.findById(dept.getParentDepartmentId())
                    .ifPresent(p -> dto.setParentDepartmentName(p.getDepartmentName()));
        }

        // Count employees in this department
        dto.setEmployeeCount(employeeRepository.countByDepartmentId(dept.getId()));

        return dto;
    }
}
