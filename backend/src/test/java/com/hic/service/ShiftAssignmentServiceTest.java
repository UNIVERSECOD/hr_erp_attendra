package com.hic.service;

import com.hic.exception.BadRequestException;
import com.hic.model.Department;
import com.hic.model.Employee;
import com.hic.model.Timetable;
import com.hic.repository.DepartmentRepository;
import com.hic.repository.EmployeeRepository;
import com.hic.repository.EmployeeShiftAssignmentRepository;
import com.hic.repository.TimetableRepository;
import com.hic.util.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ShiftAssignmentServiceTest {

    @Mock
    private EmployeeShiftAssignmentRepository assignmentRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private TimetableRepository timetableRepository;
    @Mock
    private DepartmentRepository departmentRepository;

    @InjectMocks
    private ShiftAssignmentService service;

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
    void assignEmployeeToShift_closesPreviousOverlappingAssignment() {
        Employee employee = new Employee();
        employee.setId(10L);
        employee.setTenantId(1L);
        employee.setEmploymentStatus(Employee.EmploymentStatus.ACTIVE);

        Timetable timetable = new Timetable();
        timetable.setId(20L);
        timetable.setTenantId(1L);
        timetable.setShiftType("STANDARD");

        com.hic.model.EmployeeShiftAssignment prior = new com.hic.model.EmployeeShiftAssignment();
        prior.setId(99L);
        prior.setTenantId(1L);
        prior.setEmployeeId(10L);
        prior.setTimetableId(15L);
        prior.setEffectiveStartDate(LocalDate.of(2026, 1, 1));
        prior.setEffectiveEndDate(null);
        prior.setStatus(com.hic.model.EmployeeShiftAssignment.Status.ACTIVE);

        when(employeeRepository.findById(10L)).thenReturn(Optional.of(employee));
        when(timetableRepository.findById(20L)).thenReturn(Optional.of(timetable));
        when(assignmentRepository.findOverlappingAssignments(eq(1L), eq(10L), any(), any(), isNull()))
                .thenReturn(List.of(prior))
                .thenReturn(List.of());
        when(assignmentRepository.save(any())).thenAnswer(invocation -> {
            com.hic.model.EmployeeShiftAssignment saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(100L);
            }
            return saved;
        });
        when(employeeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var dto = service.assignEmployeeToShift(10L, 20L, LocalDate.of(2026, 7, 28), null);

        assertEquals(100L, dto.getId());
        assertEquals(LocalDate.of(2026, 7, 27), prior.getEffectiveEndDate());
        assertEquals("STANDARD", employee.getShiftType());
    }

    @Test
    void assignEmployeeToShift_rejectsInvalidDateRange() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.assignEmployeeToShift(1L, 1L, LocalDate.of(2026, 6, 2), LocalDate.of(2026, 6, 1)));
        assertEquals("End date must be on or after start date", ex.getMessage());
    }

    @Test
    void bulkAssignToShift_combinesEmployeesAndDepartmentsWithoutDuplicates() {
        Employee first = activeEmployee(10L, 5L);
        Employee second = activeEmployee(11L, 5L);
        Department department = new Department();
        department.setId(5L);
        department.setTenantId(1L);

        Timetable timetable = new Timetable();
        timetable.setId(20L);
        timetable.setTenantId(1L);
        timetable.setShiftType("STANDARD");

        when(departmentRepository.findById(5L)).thenReturn(Optional.of(department));
        when(employeeRepository.findByTenantIdAndDepartmentId(1L, 5L)).thenReturn(List.of(first, second));
        when(employeeRepository.findById(10L)).thenReturn(Optional.of(first));
        when(employeeRepository.findById(11L)).thenReturn(Optional.of(second));
        when(timetableRepository.findById(20L)).thenReturn(Optional.of(timetable));
        when(assignmentRepository.findOverlappingAssignments(eq(1L), anyLong(), any(), any(), isNull()))
                .thenReturn(List.of());
        java.util.concurrent.atomic.AtomicLong ids = new java.util.concurrent.atomic.AtomicLong(100L);
        when(assignmentRepository.save(any())).thenAnswer(invocation -> {
            com.hic.model.EmployeeShiftAssignment saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(ids.getAndIncrement());
            }
            return saved;
        });
        when(employeeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.bulkAssignToShift(
                List.of(10L),
                List.of(5L),
                20L,
                LocalDate.of(2026, 9, 23),
                null
        );

        assertEquals(2, result.size());
        verify(employeeRepository, times(1)).findById(10L);
        verify(employeeRepository, times(1)).findById(11L);
    }

    private Employee activeEmployee(Long id, Long departmentId) {
        Employee employee = new Employee();
        employee.setId(id);
        employee.setTenantId(1L);
        employee.setDepartmentId(departmentId);
        employee.setEmploymentStatus(Employee.EmploymentStatus.ACTIVE);
        return employee;
    }
}
