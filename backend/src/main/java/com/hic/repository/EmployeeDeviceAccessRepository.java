package com.hic.repository;

import com.hic.model.EmployeeDeviceAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeDeviceAccessRepository extends JpaRepository<EmployeeDeviceAccess, Long> {
    List<EmployeeDeviceAccess> findByEmployeeId(Long employeeId);
    List<EmployeeDeviceAccess> findByEmployeeIdIn(List<Long> employeeIds);
    List<EmployeeDeviceAccess> findByDeviceConfigId(Long deviceConfigId);
    Optional<EmployeeDeviceAccess> findByEmployeeIdAndDeviceConfigId(Long employeeId, Long deviceConfigId);
    void deleteByEmployeeId(Long employeeId);
    boolean existsByEmployeeIdAndDeviceConfigId(Long employeeId, Long deviceConfigId);
}
