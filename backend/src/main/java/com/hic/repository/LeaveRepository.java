package com.hic.repository;

import com.hic.model.Leave;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface LeaveRepository extends JpaRepository<Leave, Long> {
    List<Leave> findByTenantId(Long tenantId);
    List<Leave> findByTenantIdAndApplyType(Long tenantId, String applyType);
    List<Leave> findByTenantIdAndTargetId(Long tenantId, Long targetId);
    List<Leave> findByTenantIdAndStatus(Long tenantId, String status);
    Optional<Leave> findByIdAndTenantId(Long id, Long tenantId);

    @Query("""
            SELECT l
            FROM Leave l
            LEFT JOIN Employee e ON l.applyType = 'EMPLOYEE' AND l.targetId = e.id AND l.tenantId = e.tenantId
            WHERE l.tenantId = :tenantId
              AND (:status IS NULL OR l.status = :status)
              AND (:type IS NULL OR LOWER(l.leaveType) = LOWER(:type))
              AND (:startDate IS NULL OR l.startDate >= :startDate)
              AND (:endDate IS NULL OR l.endDate <= :endDate)
              AND (
                    :search IS NULL
                    OR :search = ''
                    OR LOWER(COALESCE(e.firstName, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(COALESCE(e.lastName, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(CONCAT(COALESCE(e.firstName, ''), ' ', COALESCE(e.lastName, ''))) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(COALESCE(e.employeeId, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(COALESCE(e.finNumber, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(COALESCE(l.reason, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                  )
            """)
    Page<Leave> searchByFilters(@Param("tenantId") Long tenantId,
                                @Param("search") String search,
                                @Param("status") String status,
                                @Param("type") String type,
                                @Param("startDate") LocalDate startDate,
                                @Param("endDate") LocalDate endDate,
                                Pageable pageable);

    @Query("""
            SELECT l
            FROM Leave l
            WHERE l.tenantId = :tenantId
              AND l.applyType = 'EMPLOYEE'
              AND l.targetId = :employeeId
              AND l.endDate >= :fromDate
              AND l.startDate <= :toDate
            ORDER BY l.startDate DESC
            """)
    List<Leave> findEmployeeHistoryForPeriod(@Param("tenantId") Long tenantId,
                                             @Param("employeeId") Long employeeId,
                                             @Param("fromDate") LocalDate fromDate,
                                             @Param("toDate") LocalDate toDate);
}
