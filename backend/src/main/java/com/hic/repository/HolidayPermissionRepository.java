package com.hic.repository;

import com.hic.model.HolidayPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface HolidayPermissionRepository extends JpaRepository<HolidayPermission, Long> {

    List<HolidayPermission> findByTenantIdOrderByStartDateDesc(Long tenantId);

    Optional<HolidayPermission> findByIdAndTenantId(Long id, Long tenantId);

    @Query("SELECT h FROM HolidayPermission h WHERE h.tenantId = :tenantId " +
            "AND h.startDate <= :endDate AND h.endDate >= :startDate ORDER BY h.startDate ASC")
    List<HolidayPermission> findOverlapping(@Param("tenantId") Long tenantId,
                                            @Param("startDate") LocalDate startDate,
                                            @Param("endDate") LocalDate endDate);
}
