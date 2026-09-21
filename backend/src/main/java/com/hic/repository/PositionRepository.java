package com.hic.repository;

import com.hic.model.Position;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PositionRepository extends JpaRepository<Position, Long> {
    // Tenant-aware methods
    List<Position> findByTenantId(Long tenantId);
    List<Position> findByTenantIdAndDepartmentId(Long tenantId, Long departmentId);

    // Legacy methods
    List<Position> findByDepartmentId(Long departmentId);

    long countByDepartmentId(Long departmentId);
}
