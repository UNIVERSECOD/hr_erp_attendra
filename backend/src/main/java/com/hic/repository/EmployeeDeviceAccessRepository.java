package com.hic.repository;

import com.hic.model.EmployeeDeviceAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmployeeDeviceAccessRepository extends JpaRepository<EmployeeDeviceAccess, Long> {
    List<EmployeeDeviceAccess> findByEmployeeId(Long employeeId);
    List<EmployeeDeviceAccess> findByEmployeeIdIn(List<Long> employeeIds);
    void deleteByEmployeeId(Long employeeId);
    boolean existsByEmployeeIdAndDeviceConfigId(Long employeeId, Long deviceConfigId);
}
