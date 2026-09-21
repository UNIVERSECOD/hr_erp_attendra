package com.hic.service;

import com.hic.model.LeaveRequest;
import com.hic.model.LeaveRequest.LeaveStatus;
import com.hic.repository.EmployeeRepository;
import com.hic.repository.LeaveRequestRepository;
import com.hic.repository.LeaveTypeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
