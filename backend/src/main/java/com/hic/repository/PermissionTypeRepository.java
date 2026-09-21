package com.hic.repository;

import com.hic.model.PermissionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PermissionTypeRepository extends JpaRepository<PermissionType, Long> {
    List<PermissionType> findByTenantId(Long tenantId);
}
