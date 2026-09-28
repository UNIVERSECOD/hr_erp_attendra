package com.hic.repository;

import com.hic.model.EmployeeArea;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeAreaRepository extends JpaRepository<EmployeeArea, Long> {
    List<EmployeeArea> findByEmployeeIdOrderByPrimaryDescBranchIdAsc(Long employeeId);
    List<EmployeeArea> findByEmployeeIdIn(Collection<Long> employeeIds);
    List<EmployeeArea> findByBranchId(Long branchId);
    Optional<EmployeeArea> findByEmployeeIdAndBranchId(Long employeeId, Long branchId);
    boolean existsByEmployeeIdAndBranchId(Long employeeId, Long branchId);
    long countByTenantIdAndBranchId(Long tenantId, Long branchId);
    void deleteByEmployeeId(Long employeeId);
}
