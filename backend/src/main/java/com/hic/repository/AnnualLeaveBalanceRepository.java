package com.hic.repository;

import com.hic.model.AnnualLeaveBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AnnualLeaveBalanceRepository extends JpaRepository<AnnualLeaveBalance, Long> {

    Optional<AnnualLeaveBalance> findByIdAndTenantId(Long id, Long tenantId);

    Optional<AnnualLeaveBalance> findByTenantIdAndEmployeeIdAndYear(Long tenantId, Long employeeId, Integer year);

    List<AnnualLeaveBalance> findByTenantIdAndYearOrderByEmployeeIdAsc(Long tenantId, Integer year);
}
