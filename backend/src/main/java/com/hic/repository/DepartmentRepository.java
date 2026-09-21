package com.hic.repository;

import com.hic.model.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {
    // Tenant-aware methods
    List<Department> findByTenantId(Long tenantId);
    List<Department> findByTenantIdAndBranchId(Long tenantId, Long branchId);

    // Legacy methods
    List<Department> findByBranchId(Long branchId);

    // Parent department lookup
    List<Department> findByParentDepartmentId(Long parentDepartmentId);
    long countByParentDepartmentId(Long parentDepartmentId);
}
