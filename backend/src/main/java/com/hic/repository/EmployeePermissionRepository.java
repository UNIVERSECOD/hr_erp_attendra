package com.hic.repository;

import com.hic.model.EmployeePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface EmployeePermissionRepository extends JpaRepository<EmployeePermission, Long> {
    List<EmployeePermission> findByTenantId(Long tenantId);

    List<EmployeePermission> findByTenantIdAndEmployeeIdOrderByStartDateDesc(Long tenantId, Long employeeId);

    List<EmployeePermission> findByTenantIdAndPermissionTypeIdOrderByStartDateDesc(Long tenantId, Long permissionTypeId);

    @Query("SELECT e FROM EmployeePermission e WHERE e.tenantId = :tenantId AND e.employeeId = :employeeId " +
           "AND e.status IN ('ACTIVE', 'APPROVED') AND e.startDate <= :date AND e.endDate >= :date")
    List<EmployeePermission> findActiveForEmployee(@Param("tenantId") Long tenantId,
                                                   @Param("employeeId") Long employeeId,
                                                   @Param("date") LocalDate date);

    @Query("SELECT e FROM EmployeePermission e WHERE e.tenantId = :tenantId AND e.permissionTypeId = :permissionTypeId " +
           "AND e.status IN ('ACTIVE', 'APPROVED') AND e.startDate <= :date AND e.endDate >= :date")
    List<EmployeePermission> findEmployeesWithPermission(@Param("tenantId") Long tenantId,
                                                         @Param("permissionTypeId") Long permissionTypeId,
                                                         @Param("date") LocalDate date);

    @Query("SELECT e FROM EmployeePermission e WHERE e.tenantId = :tenantId AND e.startDate <= :endDate AND e.endDate >= :startDate")
    List<EmployeePermission> findByDateRange(@Param("tenantId") Long tenantId,
                                             @Param("startDate") LocalDate startDate,
                                             @Param("endDate") LocalDate endDate);

    @Query("SELECT e FROM EmployeePermission e WHERE e.tenantId = :tenantId AND e.employeeId = :employeeId " +
           "AND e.status IN ('ACTIVE', 'APPROVED') AND e.startDate <= :endDate AND e.endDate >= :startDate")
    List<EmployeePermission> findByTenantIdAndEmployeeIdAndDateRange(@Param("tenantId") Long tenantId,
                                                                     @Param("employeeId") Long employeeId,
                                                                     @Param("startDate") LocalDate startDate,
                                                                     @Param("endDate") LocalDate endDate);

    @Query("SELECT e FROM EmployeePermission e WHERE e.employeeId = :employeeId " +
           "AND e.status IN ('ACTIVE', 'APPROVED') AND e.startDate <= :endDate AND e.endDate >= :startDate")
    List<EmployeePermission> findByEmployeeIdAndDateRange(@Param("employeeId") Long employeeId,
                                                          @Param("startDate") LocalDate startDate,
                                                          @Param("endDate") LocalDate endDate);
}
