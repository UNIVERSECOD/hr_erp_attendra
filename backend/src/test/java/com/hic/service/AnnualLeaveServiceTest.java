package com.hic.service;

import com.hic.dto.AnnualLeaveBalanceDTO;
import com.hic.model.AnnualLeaveBalance;
import com.hic.model.Employee;
import com.hic.repository.AnnualLeaveBalanceRepository;
import com.hic.repository.EmployeeRepository;
import com.hic.util.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnnualLeaveServiceTest {

    @Mock
    private AnnualLeaveBalanceRepository annualLeaveBalanceRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @InjectMocks
    private AnnualLeaveService annualLeaveService;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void create_calculatesRemainingDays() {
        AnnualLeaveBalanceDTO dto = new AnnualLeaveBalanceDTO();
        dto.setEmployeeId(11L);
        dto.setYear(2026);
        dto.setEntitlementDays(20);
        dto.setUsedDays(4);
        dto.setCarryoverDays(3);
        dto.setStatus("ACTIVE");

        Employee employee = new Employee();
        employee.setId(11L);
        employee.setTenantId(1L);
        employee.setFirstName("Aysel");
        employee.setLastName("Aliyeva");

        TenantContext.setTenantId(1L);
        when(employeeRepository.findById(11L)).thenReturn(Optional.of(employee));
        when(annualLeaveBalanceRepository.findByTenantIdAndEmployeeIdAndYear(1L, 11L, 2026)).thenReturn(Optional.empty());
        when(annualLeaveBalanceRepository.save(any(AnnualLeaveBalance.class))).thenAnswer(invocation -> {
            AnnualLeaveBalance balance = invocation.getArgument(0);
            balance.setId(50L);
            return balance;
        });

        AnnualLeaveBalanceDTO created = annualLeaveService.create(dto);

        assertEquals(19, created.getRemainingDays());
        assertEquals("Aysel Aliyeva", created.getEmployeeName());
    }

    @Test
    void update_usedDaysExceedsAllowance_throwsIllegalArgument() {
        AnnualLeaveBalance balance = new AnnualLeaveBalance();
        balance.setId(7L);
        balance.setTenantId(1L);
        balance.setEmployeeId(11L);
        balance.setYear(2026);
        balance.setEntitlementDays(10);
        balance.setCarryoverDays(1);
        balance.setUsedDays(2);

        AnnualLeaveBalanceDTO dto = new AnnualLeaveBalanceDTO();
        dto.setUsedDays(20);

        TenantContext.setTenantId(1L);
        when(annualLeaveBalanceRepository.findByIdAndTenantId(7L, 1L)).thenReturn(Optional.of(balance));

        assertThrows(IllegalArgumentException.class, () -> annualLeaveService.update(7L, dto));
    }
}
