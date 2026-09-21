package com.hic.service;

import com.hic.dto.TabelMonthlyDTO;
import com.hic.model.DailyAttendanceSummary;
import com.hic.model.Employee;
import com.hic.model.HolidayPermission;
import com.hic.model.LeaveRequest;
import com.hic.model.Position;
import com.hic.repository.DailyAttendanceSummaryRepository;
import com.hic.repository.EmployeePermissionRepository;
import com.hic.repository.EmployeeRepository;
import com.hic.repository.HolidayPermissionRepository;
import com.hic.repository.LeaveRequestRepository;
import com.hic.repository.PositionRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TabelServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private PositionRepository positionRepository;
    @Mock
    private DailyAttendanceSummaryRepository dailyAttendanceSummaryRepository;
    @Mock
    private LeaveRequestRepository leaveRequestRepository;
    @Mock
    private EmployeePermissionRepository employeePermissionRepository;
    @Mock
    private HolidayPermissionRepository holidayPermissionRepository;

    @InjectMocks
    private TabelService tabelService;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void getMonthlyTabel_computesDailyValuesAndTotals() {
        TenantContext.setTenantId(7L);

        Employee employee = new Employee();
        employee.setId(11L);
        employee.setBranchId(3L);
        employee.setDepartmentId(4L);
        employee.setPositionId(5L);
        employee.setFirstName("Ayla");
        employee.setLastName("Aliyeva");
        employee.setFatherName("Rauf");
        employee.setFinNumber("FIN123");

        Position position = new Position();
        position.setId(5L);
        position.setPositionName("Mütəxəssis");

        DailyAttendanceSummary day3 = new DailyAttendanceSummary();
        day3.setAttendanceDate(LocalDate.of(2026, 4, 3));
        day3.setHoursWorked(9.0);

        DailyAttendanceSummary day4 = new DailyAttendanceSummary();
        day4.setAttendanceDate(LocalDate.of(2026, 4, 4));
        day4.setHoursWorked(8.5);

        LeaveRequest leaveRequest = new LeaveRequest();
        leaveRequest.setEmployeeId(11L);
        leaveRequest.setStartDate(LocalDate.of(2026, 4, 5));
        leaveRequest.setEndDate(LocalDate.of(2026, 4, 5));

        HolidayPermission holiday = new HolidayPermission();
        holiday.setApplyScope("COMPANY");
        holiday.setStatus("ACTIVE");
        holiday.setStartDate(LocalDate.of(2026, 4, 6));
        holiday.setEndDate(LocalDate.of(2026, 4, 6));

        when(employeeRepository.findByTenantId(7L, Pageable.unpaged())).thenReturn(new PageImpl<>(List.of(employee)));
        when(positionRepository.findByTenantId(7L)).thenReturn(List.of(position));
        when(leaveRequestRepository.findApprovedByTenantAndEmployeeIdsAndDateRange(any(), any(), any(), any()))
                .thenReturn(List.of(leaveRequest));
        when(employeePermissionRepository.findByDateRange(any(), any(), any())).thenReturn(List.of());
        when(holidayPermissionRepository.findOverlapping(any(), any(), any())).thenReturn(List.of(holiday));
        when(dailyAttendanceSummaryRepository.findByEmployeeIdAndAttendanceDateBetween(11L,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30)))
                .thenReturn(List.of(day3, day4));

        TabelMonthlyDTO result = tabelService.getMonthlyTabel(2026, 4, 3L, 4L, 5L, "FIN123");

        assertEquals(30, result.getDaysInMonth());
        assertEquals(1, result.getEmployees());
        assertEquals(1, result.getRows().size());

        TabelMonthlyDTO.RowDTO row = result.getRows().get(0);
        assertEquals("Aliyeva Ayla Rauf", row.getFullName());
        assertEquals("Mütəxəssis", row.getPosition());
        assertEquals(0, row.getDaily().get(1));
        assertEquals(9.0, row.getDaily().get(3));
        assertEquals(8.5, row.getDaily().get(4));
        assertEquals("Q/I", row.getDaily().get(5));
        assertEquals("Q/I", row.getDaily().get(6));
        assertNull(row.getDaily().get(11));
        assertEquals(2, row.getWorkingDays());
        assertEquals(17.5, row.getTotalHours());
    }
}
