package com.hic.repository;

import com.hic.model.LeaveType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LeaveTypeRepository extends JpaRepository<LeaveType, Long> {
    Optional<LeaveType> findByLeaveCode(String leaveCode);
    Optional<LeaveType> findByTenantIdAndLeaveCode(Long tenantId, String leaveCode);
    List<LeaveType> findByTenantId(Long tenantId);
}
