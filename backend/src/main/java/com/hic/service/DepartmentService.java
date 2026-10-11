package com.hic.service;

import com.hic.dto.DepartmentDTO;
import com.hic.exception.ResourceNotFoundException;
import com.hic.exception.BadRequestException;
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
import java.util.Objects;
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
        if (tenantId != null) {
            departments = departmentRepository.findByTenantId(tenantId);
        } else {
            departments = departmentRepository.findAll();
        }
        return departments.stream()
                .filter(d -> effectiveBranchId == null || Boolean.TRUE.equals(d.getAllAreas())
                        || effectiveBranchId.equals(d.getBranchId()))
                .map(this::toDTO).collect(Collectors.toList());
    }

    public List<DepartmentDTO> getByBranch(Long branchId) {
        return getAll(branchId);
    }

    public DepartmentDTO getById(Long id) {
        return toDTO(requireDepartment(id));
    }

    @Transactional
    public DepartmentDTO create(DepartmentDTO dto) {
        Long tenantId = TenantContext.getTenantId();
        validateReferences(dto);
        Department dept = new Department();
        dept.setAllAreas(Boolean.TRUE.equals(dto.getAllAreas()));
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
        Department dept = requireDepartment(id);
        validateReferences(dto);
        if (Objects.equals(id, dto.getParentDepartmentId())) {
            throw new BadRequestException("Departament özü-özünün valideyni ola bilməz.");
        }
        if (dto.getAllAreas() != null) dept.setAllAreas(dto.getAllAreas());
        dept.setDepartmentName(dto.getDepartmentName());
        dept.setDescription(dto.getDescription());
        dept.setParentDepartmentId(dto.getParentDepartmentId());
        if (dto.getCalculateOvertime() != null) dept.setCalculateOvertime(dto.getCalculateOvertime());
        if (dto.getFlexShift() != null) dept.setFlexShift(dto.getFlexShift());
        if (dto.getTimetable() != null) dept.setTimetable(dto.getTimetable());
        if (dto.getTimetableId() != null) dept.setTimetableId(dto.getTimetableId());
        if (dto.getBranchId() != null) dept.setBranchId(dto.getBranchId());
        return toDTO(departmentRepository.save(dept));
    }

    @Transactional
    public void delete(Long id) {
        requireDepartment(id);
        departmentRepository.deleteById(id);
    }

    private DepartmentDTO toDTO(Department dept) {
        DepartmentDTO dto = new DepartmentDTO();
        dto.setId(dept.getId());
        dto.setDepartmentName(dept.getDepartmentName());
        dto.setDescription(dept.getDescription());
        dto.setBranchId(dept.getBranchId());
        dto.setAllAreas(dept.getAllAreas());
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

    private Department requireDepartment(Long id) {
        Long tenant = TenantContext.getTenantId();
        return departmentRepository.findById(id)
                .filter(d -> tenant == null || tenant.equals(d.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Department", id));
    }

    private void validateReferences(DepartmentDTO dto) {
        Long tenant = TenantContext.getTenantId();
        if (dto.getBranchId() != null) {
            branchRepository.findById(dto.getBranchId())
                    .filter(b -> tenant == null || tenant.equals(b.getTenantId()))
                    .orElseThrow(() -> new ResourceNotFoundException("Branch", dto.getBranchId()));
        }
        if (dto.getParentDepartmentId() != null) requireDepartment(dto.getParentDepartmentId());
    }
}
