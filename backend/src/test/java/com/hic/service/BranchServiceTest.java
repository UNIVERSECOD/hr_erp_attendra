package com.hic.service;

import com.hic.dto.BranchDTO;
import com.hic.exception.BadRequestException;
import com.hic.exception.ResourceNotFoundException;
import com.hic.model.Branch;
import com.hic.repository.BranchRepository;
import com.hic.repository.EmployeeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BranchServiceTest {

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @InjectMocks
    private BranchService branchService;

    @Test
    void create_duplicateNameInTenant_throwsBadRequest() {
        BranchDTO dto = new BranchDTO();
        dto.setName("Narimanov");

        when(branchRepository.existsByTenantIdAndNameIgnoreCase(10L, "Narimanov")).thenReturn(true);

        assertThatThrownBy(() -> branchService.create(10L, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already exists");
        verify(branchRepository, never()).save(any(Branch.class));
    }

    @Test
    void delete_branchWithEmployees_throwsBadRequest() {
        Branch branch = new Branch();
        branch.setId(7L);
        branch.setTenantId(10L);
        branch.setName("Narimanov");

        when(branchRepository.findByIdAndTenantId(7L, 10L)).thenReturn(Optional.of(branch));
        when(employeeRepository.countByTenantIdAndBranchId(10L, 7L)).thenReturn(3L);

        assertThatThrownBy(() -> branchService.delete(10L, 7L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Cannot delete branch");
        verify(branchRepository, never()).delete(any(Branch.class));
    }

    @Test
    void update_nonExistingBranch_throwsNotFound() {
        BranchDTO dto = new BranchDTO();
        dto.setName("Updated");
        when(branchRepository.findByIdAndTenantId(55L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> branchService.update(10L, 55L, dto))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
