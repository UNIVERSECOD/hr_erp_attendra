package com.hic.service;

import com.hic.model.DailyAttendanceSummary;
import com.hic.model.Employee;
import com.hic.repository.DailyAttendanceSummaryRepository;
import com.hic.repository.DepartmentRepository;
import com.hic.repository.EmployeeRepository;
import com.hic.repository.LeaveRequestRepository;
import com.hic.repository.LeaveTypeRepository;
import com.hic.util.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock private DailyAttendanceSummaryRepository summaryRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private LeaveRequestRepository leaveRequestRepository;
    @Mock private LeaveTypeRepository leaveTypeRepository;

    @InjectMocks
    private ReportService reportService;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void attendanceReport_countsCreditedAndPermittedDaysAsPresent() {
        TenantContext.setTenantId(1L);
        Employee employee = new Employee();
        employee.setId(5L);
        employee.setFirstName("Leyla");
        employee.setLastName("Aliyeva");

        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate end = LocalDate.of(2026, 9, 30);
        when(employeeRepository.findByTenantId(1L, Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of(employee)));
        when(summaryRepository.findByEmployeeIdAndAttendanceDateBetween(5L, start, end))
                .thenReturn(List.of(
                        summary(DailyAttendanceSummary.AttendanceStatus.PERMITTED_EARLY_LEAVE, 8.0),
                        summary(DailyAttendanceSummary.AttendanceStatus.ON_PERMISSION, 8.0),
                        summary(DailyAttendanceSummary.AttendanceStatus.EARLY_LEAVE, 6.0),
                        summary(DailyAttendanceSummary.AttendanceStatus.ON_PERMISSION, 0.0)
                ));

        var result = reportService.getAttendanceReport(start, end, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPresentDays()).isEqualTo(3);
        assertThat(result.get(0).getTotalHoursWorked()).isEqualTo(22.0);
    }

    private DailyAttendanceSummary summary(
            DailyAttendanceSummary.AttendanceStatus status,
            double hours
    ) {
        DailyAttendanceSummary summary = new DailyAttendanceSummary();
        summary.setAttendanceStatus(status);
        summary.setHoursWorked(hours);
        return summary;
    }
}
