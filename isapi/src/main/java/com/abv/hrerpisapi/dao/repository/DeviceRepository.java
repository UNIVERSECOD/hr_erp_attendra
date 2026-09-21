package com.abv.hrerpisapi.dao.repository;

import com.abv.hrerpisapi.dao.entity.DeviceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeviceRepository extends JpaRepository<DeviceEntity, Long> {

    List<DeviceEntity> findByEnabledTrue();

    List<DeviceEntity> findByEnabled(boolean enabled);

    Optional<DeviceEntity> findByIp(String ip);
}
