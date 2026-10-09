package com.hic.repository;

import com.hic.model.EmployeeDeviceRemovalJob;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EmployeeDeviceRemovalJobRepository extends JpaRepository<EmployeeDeviceRemovalJob, Long> {
    List<EmployeeDeviceRemovalJob> findByTenantIdAndEmployeeIdOrderById(Long tenantId, Long employeeId);
    long countByTenantIdAndEmployeeIdAndCompletedFalse(Long tenantId, Long employeeId);

    @Query("select j.id from EmployeeDeviceRemovalJob j where j.tenantId = :tenantId " +
            "and j.completed = false and j.nextAttemptAt <= :now order by j.nextAttemptAt, j.id")
    List<Long> findDueIds(@Param("tenantId") Long tenantId, @Param("now") LocalDateTime now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from EmployeeDeviceRemovalJob j where j.tenantId = :tenantId and j.id = :id")
    Optional<EmployeeDeviceRemovalJob> lockJob(@Param("tenantId") Long tenantId, @Param("id") Long id);
}
