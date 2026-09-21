package com.hic.service;

import com.hic.dto.EmployeeShiftAssignmentDTO;
import com.hic.exception.BadRequestException;
import com.hic.exception.ResourceNotFoundException;
import com.hic.model.Employee;
import com.hic.model.EmployeeShiftAssignment;
import com.hic.model.Timetable;
import com.hic.repository.EmployeeRepository;
import com.hic.repository.EmployeeShiftAssignmentRepository;
import com.hic.repository.TimetableRepository;
import com.hic.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ShiftAssignmentService {

    private final EmployeeShiftAssignmentRepository assignmentRepository;
    private final EmployeeRepository employeeRepository;
    private final TimetableRepository timetableRepository;

    @Transactional
    public EmployeeShiftAssignmentDTO assignEmployeeToShift(Long employeeId, Long timetableId, LocalDate startDate, LocalDate endDate) {
        Long tenantId = requireTenant();
        validateDateRange(startDate, endDate);

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", employeeId));
        Timetable timetable = timetableRepository.findById(timetableId)
                .orElseThrow(() -> new ResourceNotFoundException("Timetable", timetableId));

        ensureSameTenant(tenantId, employee.getTenantId(), "Employee");
        ensureSameTenant(tenantId, timetable.getTenantId(), "Timetable");

        if (employee.getEmploymentStatus() != Employee.EmploymentStatus.ACTIVE) {
            throw new BadRequestException("Only active employees can be assigned to shifts");
        }

        Long previousTimetableId = employee.getTimetableId();
        // Preserve prior schedule history when the employee only had a live snapshot.
        ensureHistoricalAssignment(tenantId, employee, previousTimetableId, startDate);
        // Close prior overlapping ACTIVE rows so schedule changes keep historical coverage intact.
        closeOverlappingAssignments(tenantId, employeeId, startDate, endDate, null);

        EmployeeShiftAssignment assignment = new EmployeeShiftAssignment();
        assignment.setTenantId(tenantId);
        assignment.setEmployeeId(employeeId);
        assignment.setTimetableId(timetableId);
        assignment.setEffectiveStartDate(startDate);
        assignment.setEffectiveEndDate(endDate);
        assignment.setAssignedBy(TenantContext.getUserId());
        assignment.setStatus(EmployeeShiftAssignment.Status.ACTIVE);

        EmployeeShiftAssignment saved = assignmentRepository.save(assignment);

        employee.setTimetableId(timetableId);
        employee.setShiftType(timetable.getShiftType());
        employeeRepository.save(employee);

        return toDTO(saved);
    }

    /**
     * Keeps {@code employee_shift_assignments} in sync when an employee's timetable is changed
     * from the employee form (not only from the shift-assignment UI).
     * <p>
     * If the employee previously had a timetable but no covering assignment history, a closed
     * historical row is created so past attendance stays classified under the old schedule.
     */
    @Transactional
    public void syncScheduleFromEmployee(Employee employee, Long previousTimetableId, Long newTimetableId, LocalDate effectiveFrom) {
        if (employee == null || employee.getId() == null) {
            return;
        }
        Long tenantId = employee.getTenantId() != null ? employee.getTenantId() : requireTenant();
        if (Objects.equals(previousTimetableId, newTimetableId)) {
            return;
        }
        LocalDate start = effectiveFrom != null ? effectiveFrom : LocalDate.now();
        ensureHistoricalAssignment(tenantId, employee, previousTimetableId, start);
        closeOverlappingAssignments(tenantId, employee.getId(), start, null, null);

        if (newTimetableId == null) {
            return;
        }
        Timetable timetable = timetableRepository.findById(newTimetableId).orElse(null);
        if (timetable == null) {
            return;
        }

        EmployeeShiftAssignment assignment = new EmployeeShiftAssignment();
        assignment.setTenantId(tenantId);
        assignment.setEmployeeId(employee.getId());
        assignment.setTimetableId(newTimetableId);
        assignment.setEffectiveStartDate(start);
        assignment.setEffectiveEndDate(null);
        assignment.setAssignedBy(TenantContext.getUserId());
        assignment.setStatus(EmployeeShiftAssignment.Status.ACTIVE);
        assignmentRepository.save(assignment);

        employee.setShiftType(timetable.getShiftType());
    }

    /**
     * When the live employee snapshot is about to change and no assignment covers the day before
     * the change, invent a closed historical assignment for the prior timetable.
     */
    private void ensureHistoricalAssignment(Long tenantId, Employee employee, Long previousTimetableId, LocalDate newStart) {
        if (previousTimetableId == null || newStart == null) {
            return;
        }
        LocalDate previousDay = newStart.minusDays(1);
        Optional<EmployeeShiftAssignment> coveringPreviousDay =
                assignmentRepository.findActiveByEmployeeAndDate(tenantId, employee.getId(), previousDay);
        if (coveringPreviousDay.isPresent()) {
            return;
        }
        LocalDate historyStart = employee.getHireDate() != null ? employee.getHireDate() : previousDay;
        if (historyStart.isAfter(previousDay)) {
            historyStart = previousDay;
        }
        EmployeeShiftAssignment historical = new EmployeeShiftAssignment();
        historical.setTenantId(tenantId);
        historical.setEmployeeId(employee.getId());
        historical.setTimetableId(previousTimetableId);
        historical.setEffectiveStartDate(historyStart);
        historical.setEffectiveEndDate(previousDay);
        historical.setAssignedBy(TenantContext.getUserId());
        historical.setStatus(EmployeeShiftAssignment.Status.ACTIVE);
        assignmentRepository.save(historical);
    }

    private void closeOverlappingAssignments(
            Long tenantId,
            Long employeeId,
            LocalDate startDate,
            LocalDate endDate,
            Long excludeId
    ) {
        List<EmployeeShiftAssignment> overlaps = assignmentRepository.findOverlappingAssignments(
                tenantId, employeeId, startDate, endDate, excludeId);
        LocalDate closeOn = startDate.minusDays(1);
        for (EmployeeShiftAssignment prior : overlaps) {
            if (prior.getEffectiveStartDate() != null && !prior.getEffectiveStartDate().isAfter(closeOn)) {
                prior.setEffectiveEndDate(closeOn);
            } else {
                prior.setStatus(EmployeeShiftAssignment.Status.INACTIVE);
                if (prior.getEffectiveEndDate() == null || prior.getEffectiveEndDate().isAfter(startDate)) {
                    prior.setEffectiveEndDate(startDate);
                }
            }
            assignmentRepository.save(prior);
        }
    }

    @Transactional
    public EmployeeShiftAssignmentDTO updateAssignment(Long id, LocalDate startDate, LocalDate endDate) {
        Long tenantId = requireTenant();
        validateDateRange(startDate, endDate);

        EmployeeShiftAssignment assignment = findByIdAndTenant(id, tenantId);
        List<EmployeeShiftAssignment> overlaps = assignmentRepository.findOverlappingAssignments(
                tenantId,
                assignment.getEmployeeId(),
                startDate,
                endDate,
                id
        );
        if (!overlaps.isEmpty()) {
            throw new BadRequestException("Updated dates overlap with another active assignment");
        }

        assignment.setEffectiveStartDate(startDate);
        assignment.setEffectiveEndDate(endDate);
        return toDTO(assignmentRepository.save(assignment));
    }

    @Transactional
    public void removeEmployeeFromShift(Long assignmentId) {
        Long tenantId = requireTenant();
        EmployeeShiftAssignment assignment = findByIdAndTenant(assignmentId, tenantId);
        assignment.setStatus(EmployeeShiftAssignment.Status.INACTIVE);
        if (assignment.getEffectiveEndDate() == null || assignment.getEffectiveEndDate().isAfter(LocalDate.now())) {
            assignment.setEffectiveEndDate(LocalDate.now());
        }
        assignmentRepository.save(assignment);
    }

    public List<EmployeeShiftAssignmentDTO> getAll() {
        Long tenantId = requireTenant();
        return assignmentRepository.findByTenantId(tenantId).stream().map(this::toDTO).toList();
    }

    public List<EmployeeShiftAssignmentDTO> getEmployeesForShift(Long timetableId, LocalDate date) {
        Long tenantId = requireTenant();
        LocalDate targetDate = date != null ? date : LocalDate.now();
        return assignmentRepository.findActiveByTimetableAndDate(tenantId, timetableId, targetDate)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    public EmployeeShiftAssignmentDTO getActiveShiftForEmployee(Long employeeId, LocalDate date) {
        Long tenantId = requireTenant();
        LocalDate targetDate = date != null ? date : LocalDate.now();
        return assignmentRepository.findActiveByEmployeeAndDate(tenantId, employeeId, targetDate)
                .map(this::toDTO)
                .orElse(null);
    }

    public List<EmployeeShiftAssignmentDTO> getShiftHistory(Long employeeId) {
        Long tenantId = requireTenant();
        return assignmentRepository.findByTenantIdAndEmployeeIdOrderByEffectiveStartDateDesc(tenantId, employeeId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional
    public List<EmployeeShiftAssignmentDTO> bulkAssignToShift(List<Long> employeeIds, Long timetableId, LocalDate startDate, LocalDate endDate) {
        if (employeeIds == null || employeeIds.isEmpty()) {
            throw new BadRequestException("At least one employee is required");
        }
        List<EmployeeShiftAssignmentDTO> result = new ArrayList<>();
        for (Long employeeId : employeeIds) {
            try {
                result.add(assignEmployeeToShift(employeeId, timetableId, startDate, endDate));
            } catch (RuntimeException ignored) {
                // continue processing remaining employees
            }
        }
        if (result.isEmpty()) {
            throw new BadRequestException("No employee could be assigned in bulk operation");
        }
        return result;
    }

    private EmployeeShiftAssignment findByIdAndTenant(Long id, Long tenantId) {
        EmployeeShiftAssignment assignment = assignmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ShiftAssignment", id));
        ensureSameTenant(tenantId, assignment.getTenantId(), "ShiftAssignment");
        return assignment;
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null) {
            throw new BadRequestException("Start date is required");
        }
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new BadRequestException("End date must be on or after start date");
        }
    }

    private Long requireTenant() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BadRequestException("Tenant context is required");
        }
        return tenantId;
    }

    private void ensureSameTenant(Long expectedTenantId, Long actualTenantId, String resourceName) {
        if (!expectedTenantId.equals(actualTenantId)) {
            throw new BadRequestException(resourceName + " does not belong to current tenant");
        }
    }

    private EmployeeShiftAssignmentDTO toDTO(EmployeeShiftAssignment assignment) {
        EmployeeShiftAssignmentDTO dto = new EmployeeShiftAssignmentDTO();
        dto.setId(assignment.getId());
        dto.setTenantId(assignment.getTenantId());
        dto.setEmployeeId(assignment.getEmployeeId());
        dto.setTimetableId(assignment.getTimetableId());
        dto.setEffectiveStartDate(assignment.getEffectiveStartDate());
        dto.setEffectiveEndDate(assignment.getEffectiveEndDate());
        dto.setAssignedBy(assignment.getAssignedBy());
        dto.setAssignedAt(assignment.getAssignedAt());
        dto.setStatus(assignment.getStatus());
        return dto;
    }
}
