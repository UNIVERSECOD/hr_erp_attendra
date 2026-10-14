package com.hic.service;

import com.hic.model.LeaveRequest;
import com.hic.model.LeaveType;
import com.hic.model.Employee;
import com.hic.dto.LeaveRequestDTO;
import com.hic.exception.BadRequestException;
import com.hic.model.LeaveRequest.LeaveStatus;
import com.hic.repository.EmployeeRepository;
import com.hic.repository.LeaveRequestRepository;
import com.hic.repository.LeaveTypeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaveServiceTest {

    @Mock
    private LeaveRequestRepository leaveRequestRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private LeaveTypeRepository leaveTypeRepository;

    @InjectMocks
    private LeaveService leaveService;

    @AfterEach
    void clearContext() {
        com.hic.util.TenantContext.clear();
    }

    private void useTenant() {
        com.hic.util.TenantContext.setTenantId(1L);
        com.hic.util.TenantContext.setUserId(2L);
    }

    private Employee employeeWithBalance(int balance) {
        Employee employee = new Employee();
        employee.setId(7L);
        employee.setTenantId(1L);
        employee.setAnnualLeaveBalance(balance);
        when(employeeRepository.lockForTermination(1L, 7L)).thenReturn(Optional.of(employee));
        return employee;
    }

    private LeaveType annualType() {
        LeaveType type = new LeaveType();
        type.setId(5L);
        type.setTenantId(1L);
        type.setLeaveCode("ANNUAL");
        when(leaveTypeRepository.findById(5L)).thenReturn(Optional.of(type));
        return type;
    }

    @Test
    void approvedAnnualLeaveChargesBalanceExactlyOnce() {
        useTenant();
        Employee employee = employeeWithBalance(10);
        annualType();
        LocalDate start = LocalDate.of(2026, 11, 2);
        LeaveRequestDTO input = new LeaveRequestDTO();
        input.setEmployeeId(7L); input.setLeaveTypeId(5L);
        input.setStartDate(start); input.setEndDate(start.plusDays(2));
        input.setStatus(LeaveStatus.APPROVED);
        input.setDeductFromLeaveBalance(true);
        when(leaveRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        LeaveRequestDTO saved = leaveService.create(input);

        assertThat(saved.getBalanceChargedDays()).isEqualTo(3);
        assertThat(saved.getDeductFromLeaveBalance()).isTrue();
        assertThat(employee.getAnnualLeaveBalance()).isEqualTo(7);
        verify(employeeRepository).save(employee);
    }

    @Test
    void insufficientBalanceRejectsApprovalWithoutSaving() {
        useTenant();
        Employee employee = employeeWithBalance(1);
        annualType();
        LeaveRequestDTO input = new LeaveRequestDTO();
        input.setEmployeeId(7L); input.setLeaveTypeId(5L);
        input.setStartDate(LocalDate.of(2026, 11, 2)); input.setEndDate(LocalDate.of(2026, 11, 4));
        input.setStatus(LeaveStatus.APPROVED);
        input.setDeductFromLeaveBalance(true);

        assertThatThrownBy(() -> leaveService.create(input)).isInstanceOf(BadRequestException.class);
        assertThat(employee.getAnnualLeaveBalance()).isEqualTo(1);
        verify(leaveRequestRepository, never()).save(any());
    }

    @Test
    void nonAnnualLeaveCanChargeBalanceWhenSelected() {
        useTenant();
        Employee employee = employeeWithBalance(10);
        LeaveType type = annualType();
        type.setLeaveCode("MEDICAL");
        LeaveRequestDTO input = new LeaveRequestDTO();
        input.setEmployeeId(7L); input.setLeaveTypeId(5L);
        input.setStartDate(LocalDate.of(2026, 11, 2)); input.setEndDate(LocalDate.of(2026, 11, 3));
        input.setStatus(LeaveStatus.APPROVED);
        input.setDeductFromLeaveBalance(true);
        when(leaveRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        LeaveRequestDTO saved = leaveService.create(input);

        assertThat(saved.getBalanceChargedDays()).isEqualTo(2);
        assertThat(employee.getAnnualLeaveBalance()).isEqualTo(8);
    }

    @Test
    void annualLeaveDoesNotChargeWithoutExplicitSelection() {
        useTenant();
        Employee employee = employeeWithBalance(10);
        annualType();
        LeaveRequestDTO input = new LeaveRequestDTO();
        input.setEmployeeId(7L); input.setLeaveTypeId(5L);
        input.setStartDate(LocalDate.of(2026, 11, 2)); input.setEndDate(LocalDate.of(2026, 11, 3));
        input.setStatus(LeaveStatus.APPROVED);
        when(leaveRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        LeaveRequestDTO saved = leaveService.create(input);

        assertThat(saved.getDeductFromLeaveBalance()).isFalse();
        assertThat(saved.getBalanceChargedDays()).isZero();
        assertThat(employee.getAnnualLeaveBalance()).isEqualTo(10);
        verify(employeeRepository, never()).save(any());
    }

    @Test
    void pendingLeaveChargesOnApprovalButNotOnRepeatedApproval() {
        useTenant();
        Employee employee = employeeWithBalance(10);
        annualType();
        LeaveRequestDTO input = new LeaveRequestDTO();
        input.setEmployeeId(7L); input.setLeaveTypeId(5L);
        input.setStartDate(LocalDate.of(2026, 11, 2)); input.setEndDate(LocalDate.of(2026, 11, 3));
        input.setStatus(LeaveStatus.PENDING);
        input.setDeductFromLeaveBalance(true);
        when(leaveRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        LeaveRequestDTO created = leaveService.create(input);
        assertThat(employee.getAnnualLeaveBalance()).isEqualTo(10);
        assertThat(created.getBalanceChargedDays()).isZero();

        LeaveRequest stored = new LeaveRequest();
        stored.setId(9L); stored.setTenantId(1L); stored.setEmployeeId(7L);
        stored.setLeaveTypeId(5L); stored.setStartDate(input.getStartDate()); stored.setEndDate(input.getEndDate());
        stored.setStatus(LeaveStatus.PENDING); stored.setDeductFromLeaveBalance(true);
        when(leaveRequestRepository.findById(9L)).thenReturn(Optional.of(stored));

        leaveService.updateStatus(9L, LeaveStatus.APPROVED, null);
        leaveService.updateStatus(9L, LeaveStatus.APPROVED, null);

        assertThat(employee.getAnnualLeaveBalance()).isEqualTo(8);
        assertThat(stored.getBalanceChargedDays()).isEqualTo(2);
    }

    @Test
    void rejectingChargedLeaveRefundsButLegacyApprovedLeaveDoesNot() {
        useTenant();
        Employee employee = employeeWithBalance(7);
        LeaveRequest leave = new LeaveRequest();
        leave.setId(9L); leave.setTenantId(1L); leave.setEmployeeId(7L);
        leave.setStatus(LeaveStatus.APPROVED); leave.setBalanceChargedDays(3);
        when(leaveRequestRepository.findById(9L)).thenReturn(Optional.of(leave));
        when(leaveRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        leaveService.updateStatus(9L, LeaveStatus.REJECTED, null);

        assertThat(employee.getAnnualLeaveBalance()).isEqualTo(10);
        assertThat(leave.getBalanceChargedDays()).isZero();
        leave.setStatus(LeaveStatus.APPROVED); // pre-existing approved row, never charged
        leaveService.updateStatus(9L, LeaveStatus.REJECTED, null);
        assertThat(employee.getAnnualLeaveBalance()).isEqualTo(10);
    }

    @Test
    void hasActiveLeave_returnsTrueForDateInsideApprovedRange() {
        LeaveRequest request = new LeaveRequest();
        request.setStatus(LeaveStatus.APPROVED);
        request.setStartDate(LocalDate.of(2026, 5, 10));
        request.setEndDate(LocalDate.of(2026, 5, 12));
        when(leaveRequestRepository.findByEmployeeIdAndStatus(1L, LeaveStatus.APPROVED)).thenReturn(List.of(request));

        boolean active = leaveService.hasActiveLeave(1L, LocalDate.of(2026, 5, 11));

        assertThat(active).isTrue();
    }

    @Test
    void hasActiveLeave_returnsFalseWhenNoApprovedLeaveIncludesDate() {
        LeaveRequest request = new LeaveRequest();
        request.setStatus(LeaveStatus.APPROVED);
        request.setStartDate(LocalDate.of(2026, 5, 10));
        request.setEndDate(LocalDate.of(2026, 5, 12));
        when(leaveRequestRepository.findByEmployeeIdAndStatus(1L, LeaveStatus.APPROVED)).thenReturn(List.of(request));

        boolean active = leaveService.hasActiveLeave(1L, LocalDate.of(2026, 5, 20));

        assertThat(active).isFalse();
    }
}
