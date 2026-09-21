package com.hic.repository;

import com.hic.model.Door;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DoorRepository extends JpaRepository<Door, Long> {
    List<Door> findByTenantIdAndBranchId(Long tenantId, Long branchId);
    List<Door> findByTenantId(Long tenantId);
    List<Door> findByBranchId(Long branchId);
    Optional<Door> findByIdAndTenantId(Long id, Long tenantId);
}
