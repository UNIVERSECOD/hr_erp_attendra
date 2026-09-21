package com.hic.service;

import com.hic.dto.BranchDTO;
import com.hic.exception.BadRequestException;
import com.hic.exception.ResourceNotFoundException;
import com.hic.model.Branch;
import com.hic.repository.BranchRepository;
import com.hic.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BranchService {

    private final BranchRepository branchRepository;
    private final EmployeeRepository employeeRepository;

    public List<BranchDTO> getAll(Long tenantId) {
        List<Branch> branches = branchRepository.findByTenantId(tenantId);
        return branches.stream().map(this::toDTO).collect(Collectors.toList());
    }

    public BranchDTO getById(Long tenantId, Long id) {
        return toDTO(branchRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch", id)));
    }

    @Transactional
    public BranchDTO create(Long tenantId, BranchDTO dto) {
        if (branchRepository.existsByTenantIdAndNameIgnoreCase(tenantId, dto.getName())) {
            throw new BadRequestException("Branch name already exists: " + dto.getName());
        }

        Branch branch = new Branch();
        branch.setName(dto.getName());
        branch.setCode(dto.getCode());
        branch.setCity(dto.getCity());
        branch.setAddress(dto.getAddress());
        branch.setStatus(dto.getStatus() != null ? dto.getStatus() : "ACTIVE");
        branch.setIsHeadOffice(dto.getIsHeadOffice() != null ? dto.getIsHeadOffice() : false);
        branch.setTenantId(tenantId);
        return toDTO(branchRepository.save(branch));
    }

    @Transactional
    public BranchDTO update(Long tenantId, Long id, BranchDTO dto) {
        Branch branch = branchRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch", id));

        if (!branch.getName().equalsIgnoreCase(dto.getName())
                && branchRepository.existsByTenantIdAndNameIgnoreCase(tenantId, dto.getName())) {
            throw new BadRequestException("Branch name already exists: " + dto.getName());
        }

        branch.setName(dto.getName());
        branch.setCode(dto.getCode());
        branch.setCity(dto.getCity());
        branch.setAddress(dto.getAddress());
        branch.setStatus(dto.getStatus() != null ? dto.getStatus() : branch.getStatus());
        branch.setIsHeadOffice(dto.getIsHeadOffice() != null ? dto.getIsHeadOffice() : branch.getIsHeadOffice());
        return toDTO(branchRepository.save(branch));
    }

    @Transactional
    public void delete(Long tenantId, Long id) {
        Branch branch = branchRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch", id));

        if (employeeRepository.countByTenantIdAndBranchId(tenantId, branch.getId()) > 0) {
            throw new BadRequestException("Cannot delete branch with assigned employees");
        }

        branchRepository.delete(branch);
    }

    private BranchDTO toDTO(Branch branch) {
        BranchDTO dto = new BranchDTO();
        dto.setId(branch.getId());
        dto.setName(branch.getName());
        dto.setCode(branch.getCode());
        dto.setCity(branch.getCity());
        dto.setAddress(branch.getAddress());
        dto.setStatus(branch.getStatus());
        dto.setIsHeadOffice(branch.getIsHeadOffice());
        return dto;
    }
}
