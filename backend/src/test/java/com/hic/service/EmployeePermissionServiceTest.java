package com.hic.service;

import com.hic.exception.BadRequestException;
import com.hic.exception.ResourceNotFoundException;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
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
    @Mock
    private AttendanceScheduleResolver scheduleResolver;

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
        when(scheduleResolver.resolveForPermission(employee, today)).thenReturn(schedule(true, 9, 18));

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

    private AttendanceScheduleResolver.DaySchedule schedule(boolean working, int start, int end) {
        return new AttendanceScheduleResolver.DaySchedule(1L, "STANDARD", working,
                LocalTime.of(start, 0), LocalTime.of(end, 0), 0, 0, 0);
    }

    private Employee setupEmployee(long id) {
        Employee employee = new Employee();
        employee.setId(id); employee.setTenantId(1L); employee.setEmployeeId("TEST-" + id);
        when(employeeRepository.findById(id)).thenReturn(java.util.Optional.of(employee));
        return employee;
    }

    private void setupType() {
        PermissionType type = new PermissionType(); type.setId(4L); type.setTenantId(1L);
        when(permissionTypeRepository.findById(4L)).thenReturn(java.util.Optional.of(type));
    }

    @Test
    void grantRejectsPartlyOutsideHoursAndRestDayIncludingFullDay() {
        var today = AppTimeZone.today();
        var employee = setupEmployee(1L); setupType();
        when(scheduleResolver.resolveForPermission(employee, today)).thenReturn(schedule(true, 9, 18));
        for (var hours : List.of(new int[]{8, 10}, new int[]{17, 19}, new int[]{19, 20})) {
            assertThrows(BadRequestException.class, () -> service.grantPermission(1L, 4L, today, today,
                    LocalTime.of(hours[0], 0), LocalTime.of(hours[1], 0), true, "note", null));
        }
        when(scheduleResolver.resolveForPermission(employee, today)).thenReturn(schedule(false, 9, 18));
        assertThrows(BadRequestException.class, () -> service.grantPermission(1L, 4L, today, today,
                null, null, true, "note", null));
        verify(permissionRepository, never()).save(any());
    }

    @Test
    void grantChecksEveryDateBeforeSaving() {
        var today = AppTimeZone.today(); var employee = setupEmployee(1L); setupType();
        when(scheduleResolver.resolveForPermission(employee, today)).thenReturn(schedule(true, 9, 18));
        when(scheduleResolver.resolveForPermission(employee, today.plusDays(1))).thenReturn(schedule(false, 9, 18));
        assertThrows(BadRequestException.class, () -> service.grantPermission(1L, 4L, today, today.plusDays(1),
                null, null, true, "note", null));
        verify(permissionRepository, never()).save(any());
    }

    @Test
    void bulkChecksAllSchedulesBeforeAnySave() {
        var today = AppTimeZone.today(); var first = setupEmployee(1L); var second = setupEmployee(2L); setupType();
        when(scheduleResolver.resolveForPermission(first, today)).thenReturn(schedule(true, 9, 18));
        when(scheduleResolver.resolveForPermission(second, today)).thenReturn(schedule(true, 12, 20));
        assertThrows(BadRequestException.class, () -> service.bulkGrantPermission(List.of(1L, 2L), 4L, today, today,
                LocalTime.of(10, 0), LocalTime.of(11, 0), true, "note", null));
        verify(permissionRepository, never()).save(any());
    }

    @Test
    void overnightAllowsMidnightAndAfterMidnightButRejectsOutsideShift() {
        var today = AppTimeZone.today(); var employee = setupEmployee(1L); setupType();
        when(scheduleResolver.resolveForPermission(employee, today)).thenReturn(schedule(true, 20, 4));
        when(permissionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        for (var hours : List.of(new int[]{20, 4}, new int[]{23, 2}, new int[]{2, 4})) {
            service.grantPermission(1L, 4L, today, today, LocalTime.of(hours[0], 0),
                    LocalTime.of(hours[1], 0), true, "note", null);
        }
        assertThrows(BadRequestException.class, () -> service.grantPermission(1L, 4L, today, today,
                LocalTime.of(3, 0), LocalTime.of(5, 0), true, "note", null));
    }

    @Test
    void updateValidatesBeforeMutationAndRecalculatesOldAndNewDates() {
        var today = AppTimeZone.today(); var employee = setupEmployee(1L);
        var permission = new EmployeePermission(); permission.setTenantId(1L); permission.setEmployeeId(1L);
        permission.setStartDate(today.minusDays(1)); permission.setEndDate(today); permission.setDeductFromWorkHours(false);
        when(permissionRepository.findById(8L)).thenReturn(java.util.Optional.of(permission));
        when(scheduleResolver.resolveForPermission(employee, today)).thenReturn(schedule(true, 9, 18));
        assertThrows(BadRequestException.class, () -> service.updatePermission(8L, today, today,
                LocalTime.of(17, 0), LocalTime.of(19, 0), true, "note", null));
        assertThat(permission.getDeductFromWorkHours()).isFalse();
        when(permissionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        service.updatePermission(8L, today, today, LocalTime.of(10, 0), LocalTime.of(11, 0), true, "note", null);
        assertThat(permission.getDeductFromWorkHours()).isTrue();
        for (var date : List.of(today.minusDays(1), today)) {
            verify(attendanceCalculationService).calculateForDay(1L, date);
            verify(attendanceService).generateDailySummary(1L, date);
        }
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

    @Test
    void grantPermission_rejectsHourlyDateRange() {
        LocalDate today = AppTimeZone.today();

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.grantPermission(
                        1L, 1L, today, today.plusDays(1),
                        LocalTime.of(10, 0), LocalTime.of(11, 0),
                        false, "note", EmployeePermission.Status.APPROVED));

        assertEquals("Saatlıq icazə yalnız bir tarix üçün verilə bilər.", ex.getMessage());
        verify(permissionRepository, never()).save(any());
    }

    @Test
    void bulkGrantPermission_validatesEveryEmployeeBeforeSaving() {
        LocalDate today = AppTimeZone.today();
        Employee employee = new Employee();
        employee.setId(1L);
        employee.setTenantId(1L);

        PermissionType permissionType = new PermissionType();
        permissionType.setId(4L);
        permissionType.setTenantId(1L);
        permissionType.setCode("HOURLY_PERMISSION");

        when(permissionTypeRepository.findById(4L)).thenReturn(java.util.Optional.of(permissionType));
        when(employeeRepository.findById(1L)).thenReturn(java.util.Optional.of(employee));
        when(employeeRepository.findById(2L)).thenReturn(java.util.Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.bulkGrantPermission(
                List.of(1L, 2L), 4L, today, today,
                LocalTime.of(15, 0), LocalTime.of(17, 0),
                false, "Ailə işi", EmployeePermission.Status.APPROVED));

        verify(permissionRepository, never()).save(any(EmployeePermission.class));
    }
}
