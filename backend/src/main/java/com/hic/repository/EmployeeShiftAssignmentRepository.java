package com.hic.repository;

import com.hic.model.EmployeeShiftAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeShiftAssignmentRepository extends JpaRepository<EmployeeShiftAssignment, Long> {
    List<EmployeeShiftAssignment> findByTenantId(Long tenantId);

    List<EmployeeShiftAssignment> findByTenantIdAndEmployeeIdOrderByEffectiveStartDateDesc(Long tenantId, Long employeeId);

    @Query("SELECT e FROM EmployeeShiftAssignment e WHERE e.tenantId = :tenantId AND e.timetableId = :timetableId " +
           "AND e.status = 'ACTIVE' AND e.effectiveStartDate <= :date " +
           "AND (e.effectiveEndDate IS NULL OR e.effectiveEndDate >= :date)")
    List<EmployeeShiftAssignment> findActiveByTimetableAndDate(@Param("tenantId") Long tenantId,
                                                               @Param("timetableId") Long timetableId,
                                                               @Param("date") LocalDate date);

    @Query("SELECT e FROM EmployeeShiftAssignment e WHERE e.tenantId = :tenantId AND e.employeeId = :employeeId " +
           "AND e.status = 'ACTIVE' AND e.effectiveStartDate <= :date " +
           "AND (e.effectiveEndDate IS NULL OR e.effectiveEndDate >= :date)")
    Optional<EmployeeShiftAssignment> findActiveByEmployeeAndDate(@Param("tenantId") Long tenantId,
                                                                  @Param("employeeId") Long employeeId,
                                                                  @Param("date") LocalDate date);

    @Query("SELECT e FROM EmployeeShiftAssignment e WHERE e.tenantId = :tenantId AND e.employeeId IN :employeeIds " +
           "AND e.status = 'ACTIVE' AND e.effectiveStartDate <= :endDate " +
           "AND (e.effectiveEndDate IS NULL OR e.effectiveEndDate >= :startDate)")
    List<EmployeeShiftAssignment> findActiveForEmployeesInDateRange(@Param("tenantId") Long tenantId,
                                                                    @Param("employeeIds") java.util.Collection<Long> employeeIds,
                                                                    @Param("startDate") LocalDate startDate,
                                                                    @Param("endDate") LocalDate endDate);

    @Query("SELECT e FROM EmployeeShiftAssignment e WHERE e.tenantId = :tenantId AND e.employeeId = :employeeId " +
           "AND e.status = 'ACTIVE' AND (:excludeId IS NULL OR e.id <> :excludeId) " +
           "AND e.effectiveStartDate <= COALESCE(:endDate, e.effectiveStartDate) " +
           "AND (e.effectiveEndDate IS NULL OR e.effectiveEndDate >= :startDate)")
    List<EmployeeShiftAssignment> findOverlappingAssignments(@Param("tenantId") Long tenantId,
                                                             @Param("employeeId") Long employeeId,
                                                             @Param("startDate") LocalDate startDate,
                                                             @Param("endDate") LocalDate endDate,
                                                             @Param("excludeId") Long excludeId);
}
