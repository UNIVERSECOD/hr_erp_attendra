package com.abv.hrerpisapi.dao.repository;

import com.abv.hrerpisapi.dao.entity.DeviceUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeviceUserRepository extends JpaRepository<DeviceUserEntity, Long> {

    Optional<DeviceUserEntity> findByEmployeeNo(String employeeNo);

    boolean existsByEmployeeNo(String employeeNo);
}
