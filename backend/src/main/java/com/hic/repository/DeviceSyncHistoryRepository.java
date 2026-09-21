package com.hic.repository;

import com.hic.model.DeviceSyncHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeviceSyncHistoryRepository extends JpaRepository<DeviceSyncHistory, Long> {
    List<DeviceSyncHistory> findByDeviceIdOrderBySyncStartTimeDesc(String deviceId);
    List<DeviceSyncHistory> findByDeviceId(String deviceId);
    List<DeviceSyncHistory> findTop10ByOrderByCreatedAtDesc();
}
