package com.hic.repository;

import com.hic.model.DeviceConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceConfigRepository extends JpaRepository<DeviceConfig, Long> {
    // Tenant-aware methods
    List<DeviceConfig> findByTenantId(Long tenantId);
    List<DeviceConfig> findByTenantIdAndStatus(Long tenantId, String status);
    Optional<DeviceConfig> findByTenantIdAndDeviceId(Long tenantId, String deviceId);
    long countByTenantIdAndStatus(Long tenantId, String status);
    long countByTenantId(Long tenantId);

    // Legacy methods
    List<DeviceConfig> findByBranchId(Long branchId);
    List<DeviceConfig> findByStatus(String status);
    Optional<DeviceConfig> findByDeviceId(String deviceId);
    Optional<DeviceConfig> findByDeviceIp(String deviceIp);
    long countByStatus(String status);

    // Door-related methods
    List<DeviceConfig> findByDoorId(Long doorId);
    Optional<DeviceConfig> findByDoorIdAndDoorRole(Long doorId, String doorRole);
    boolean existsByDoorIdAndDoorRole(Long doorId, String doorRole);
}
