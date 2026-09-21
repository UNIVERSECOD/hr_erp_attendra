package com.hic.repository;

import com.hic.model.AttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {
    Optional<AttendanceRecord> findByEmployeeIdAndWorkDate(Long employeeId, LocalDate workDate);
    List<AttendanceRecord> findByEmployeeIdAndWorkDateBetween(Long employeeId, LocalDate start, LocalDate end);
    List<AttendanceRecord> findByTenantIdAndWorkDateBetween(Long tenantId, LocalDate start, LocalDate end);
    List<AttendanceRecord> findByWorkDateBetween(LocalDate start, LocalDate end);
}
