package com.hic.service;

import com.hic.dto.PermissionDTO;
import com.hic.exception.BadRequestException;
import com.hic.model.Leave;
import com.hic.repository.EmployeeRepository;
import com.hic.repository.LeaveRepository;
import com.hic.repository.PermissionTypeRepository;
import com.hic.util.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    @Mock
    private LeaveRepository leaveRepository;
    @Mock
    private PermissionTypeRepository permissionTypeRepository;
    @Mock
    private EmployeeRepository employeeRepository;

    @InjectMocks
    private PermissionService permissionService;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
        TenantContext.setUserId(7L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void create_rejectsInvalidDateRange() {
        PermissionDTO dto = new PermissionDTO();
        dto.setApplyType("EMPLOYEE");
        dto.setTargetId(3L);
        dto.setStartDate(LocalDate.now().toString());
        dto.setEndDate(LocalDate.now().minusDays(1).toString());

        BadRequestException ex = assertThrows(BadRequestException.class, () -> permissionService.create(dto));
        assertEquals("End date must be on or after start date", ex.getMessage());
    }

    @Test
    void create_requiresTargetForNonCompanyApplyType() {
        PermissionDTO dto = new PermissionDTO();
        dto.setApplyType("EMPLOYEE");
        dto.setStartDate(LocalDate.now().toString());
        dto.setEndDate(LocalDate.now().toString());

        BadRequestException ex = assertThrows(BadRequestException.class, () -> permissionService.create(dto));
        assertEquals("Target is required when applyType is not COMPANY", ex.getMessage());
    }

    @Test
    void approve_setsAuditFields() {
        Leave leave = new Leave();
        leave.setId(12L);
        leave.setTenantId(1L);
        leave.setApplyType("COMPANY");
        leave.setStartDate(LocalDate.now());
        leave.setEndDate(LocalDate.now());
        leave.setName("Annual");
        leave.setLeaveType("Annual");
        leave.setStatus("PENDING");

        when(leaveRepository.findByIdAndTenantId(12L, 1L)).thenReturn(Optional.of(leave));
        when(leaveRepository.save(any(Leave.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PermissionDTO approved = permissionService.approve(12L);
        assertEquals("APPROVED", approved.getStatus());
        assertEquals(7L, approved.getApprovedBy());
    }
}
