package com.hic.service;

import com.hic.dto.DepartmentDTO;
import com.hic.exception.ResourceNotFoundException;
import com.hic.model.Branch;
import com.hic.model.Department;
import com.hic.repository.*;
import com.hic.util.TenantContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class DepartmentServiceTest {
    @Mock DepartmentRepository departmentRepository;
    @Mock BranchRepository branchRepository;
    @Mock EmployeeRepository employeeRepository;
    @Mock UserScopeService userScopeService;
    @InjectMocks DepartmentService service;

    @BeforeEach void setup() { TenantContext.setTenantId(7L); }
    @AfterEach void cleanup() { TenantContext.clear(); }

    @Test void areaListIncludesSharedDepartmentsAndHonorsUserScope() {
        Department shared = department(1L, 20L, true);
        Department local = department(2L, 10L, false);
        Department otherArea = department(3L, 20L, false);
        when(departmentRepository.findByTenantId(7L)).thenReturn(List.of(shared, local, otherArea));
        when(userScopeService.resolveBranchScope(20L)).thenReturn(10L);
        assertThat(service.getByBranch(20L)).extracting(DepartmentDTO::getId).containsExactly(1L, 2L);
    }

    @Test void enablingAllAreasPreservesIdentityAreaAndSchedule() {
        Department existing = department(1L, 10L, false);
        existing.setTimetableId(50L); existing.setCalculateOvertime(true); existing.setFlexShift(true);
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(departmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        DepartmentDTO dto = new DepartmentDTO(); dto.setDepartmentName("Maliyyə"); dto.setAllAreas(true);
        var result = service.update(1L, dto);
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getBranchId()).isEqualTo(10L);
        assertThat(result.getAllAreas()).isTrue();
        assertThat(result.getTimetableId()).isEqualTo(50L);
        assertThat(result.getCalculateOvertime()).isTrue();
        assertThat(result.getFlexShift()).isTrue();
        verify(employeeRepository, never()).save(any());
    }

    @Test void createsSharedDepartmentWithTenantAnchorAndKeepsLegacyUpdatesShared() {
        Branch anchor = new Branch(); anchor.setId(10L); anchor.setTenantId(7L); anchor.setIsHeadOffice(true);
        when(branchRepository.findByTenantId(7L)).thenReturn(List.of(anchor));
        when(departmentRepository.save(any())).thenAnswer(i -> { Department d = i.getArgument(0); d.setId(1L); return d; });
        DepartmentDTO dto = new DepartmentDTO(); dto.setDepartmentName("Ümumi"); dto.setAllAreas(true);
        var created = service.create(dto);
        assertThat(created.getAllAreas()).isTrue(); assertThat(created.getBranchId()).isEqualTo(10L);
        Department existing = department(1L, 10L, true);
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(existing));
        dto.setAllAreas(null);
        assertThat(service.update(1L, dto).getAllAreas()).isTrue();
    }

    @Test void refusesAnotherTenantsDepartmentOrArea() {
        Department foreign = department(1L, 10L, true); foreign.setTenantId(8L);
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(foreign));
        DepartmentDTO dto = new DepartmentDTO(); dto.setDepartmentName("Test");
        assertThatThrownBy(() -> service.update(1L, dto)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(ResourceNotFoundException.class);
        Branch area = new Branch(); area.setId(20L); area.setTenantId(8L);
        when(branchRepository.findById(20L)).thenReturn(Optional.of(area)); dto.setBranchId(20L);
        assertThatThrownBy(() -> service.create(dto)).isInstanceOf(ResourceNotFoundException.class);
        verify(departmentRepository, never()).save(any());
    }

    private Department department(Long id, Long area, boolean shared) {
        Department d = new Department(); d.setId(id); d.setTenantId(7L); d.setBranchId(area);
        d.setDepartmentName("Departament " + id); d.setAllAreas(shared); return d;
    }
}
