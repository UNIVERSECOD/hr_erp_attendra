package com.hic.service;

import com.hic.exception.BadRequestException;
import com.hic.model.Employee;
import com.hic.model.EmployeePermission;
import com.hic.model.PermissionType;
import com.hic.util.AppTimeZone;
import com.hic.util.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeePermissionServiceTest {

    @Mock
    private com.hic.repository.EmployeePermissionRepository permissionRepository;
    @Mock
    private com.hic.repository.EmployeeRepository employeeRepository;
    @Mock
    private com.hic.repository.PermissionTypeRepository permissionTypeRepository;
    @Mock
    private AttendanceCalculationService attendanceCalculationService;
    @Mock
    private AttendanceService attendanceService;

    @InjectMocks
    private EmployeePermissionService service;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
        TenantContext.setUserId(2L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void grantPermission_rejectsExpiredPermission() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.grantPermission(
                        1L, 1L, LocalDate.now().minusDays(5), LocalDate.now().minusDays(1),
                        null, null, true, "note", null));
        assertEquals("Cannot assign expired permission", ex.getMessage());
    }

    @Test
    void grantPermission_rejectsInvalidDateRange() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.grantPermission(
                        1L, 1L, LocalDate.now(), LocalDate.now().minusDays(1),
                        null, null, true, "note", null));
        assertEquals("End date must be on or after start date", ex.getMessage());
    }

    @Test
    void grantPermission_savesHourlyPolicyAndRecalculatesToday() {
        LocalDate today = AppTimeZone.today();
        Employee employee = new Employee();
        employee.setId(1L);
        employee.setTenantId(1L);
        PermissionType permissionType = new PermissionType();
        permissionType.setId(4L);
        permissionType.setTenantId(1L);
        permissionType.setCode("HOURLY_PERMISSION");

        when(employeeRepository.findById(1L)).thenReturn(java.util.Optional.of(employee));
        when(permissionTypeRepository.findById(4L)).thenReturn(java.util.Optional.of(permissionType));
        when(permissionRepository.save(any(EmployeePermission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.grantPermission(
                1L, 4L, today, today,
                LocalTime.of(15, 0), LocalTime.of(17, 0),
                false, "Ailə işi", EmployeePermission.Status.APPROVED
        );

        assertThat(result.getStartTime()).isEqualTo(LocalTime.of(15, 0));
        assertThat(result.getEndTime()).isEqualTo(LocalTime.of(17, 0));
        assertThat(result.getDeductFromWorkHours()).isFalse();
        assertThat(result.getReason()).isEqualTo("Ailə işi");
        verify(attendanceCalculationService).calculateForDay(1L, today);
        verify(attendanceService).generateDailySummary(1L, today);
    }

    @Test
    void grantPermission_rejectsEqualHourlyTimes() {
        LocalDate today = AppTimeZone.today();

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.grantPermission(
                        1L, 1L, today, today,
                        LocalTime.of(15, 0), LocalTime.of(15, 0),
                        false, "note", EmployeePermission.Status.APPROVED));

        assertEquals("Start and end time must be different", ex.getMessage());
    }
}
