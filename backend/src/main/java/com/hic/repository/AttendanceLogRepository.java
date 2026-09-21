package com.hic.repository;

import com.hic.model.AttendanceLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AttendanceLogRepository extends JpaRepository<AttendanceLog, Long> {

    // Tenant-aware methods
    List<AttendanceLog> findByTenantIdAndCheckInTimeBetween(Long tenantId, LocalDateTime start, LocalDateTime end);
    List<AttendanceLog> findByTenantIdAndEmployeeIdAndCheckInTimeBetween(Long tenantId, Long employeeId,
                                                                          LocalDateTime start, LocalDateTime end);
    long countByTenantIdAndCheckInTimeBetween(Long tenantId, LocalDateTime start, LocalDateTime end);

    @Query("""
            select count(distinct attendance.employeeId)
            from AttendanceLog attendance
            where attendance.tenantId = :tenantId
              and attendance.checkInTime >= :start
              and attendance.checkInTime < :endExclusive
            """)
    long countDistinctEmployeesCheckedIn(@Param("tenantId") Long tenantId,
                                         @Param("start") LocalDateTime start,
                                         @Param("endExclusive") LocalDateTime endExclusive);

    @Query("""
            select count(distinct attendance.employeeId)
            from AttendanceLog attendance
            where attendance.checkInTime >= :start
              and attendance.checkInTime < :endExclusive
            """)
    long countDistinctEmployeesCheckedIn(@Param("start") LocalDateTime start,
                                         @Param("endExclusive") LocalDateTime endExclusive);

    java.util.Optional<AttendanceLog> findByTenantIdAndEmployeeIdAndCheckInTime(Long tenantId, Long employeeId, LocalDateTime checkInTime);
    java.util.Optional<AttendanceLog> findByEmployeeIdAndCheckInTime(Long employeeId, LocalDateTime checkInTime);

    java.util.Optional<AttendanceLog> findFirstByTenantIdAndEmployeeIdAndCheckOutTimeIsNullOrderByCheckInTimeDesc(
            Long tenantId, Long employeeId);
    java.util.Optional<AttendanceLog> findFirstByEmployeeIdAndCheckOutTimeIsNullOrderByCheckInTimeDesc(Long employeeId);

    java.util.Optional<AttendanceLog> findFirstByTenantIdAndEmployeeIdAndCheckOutTimeIsNotNullOrderByCheckOutTimeDesc(
            Long tenantId, Long employeeId);
    java.util.Optional<AttendanceLog> findFirstByEmployeeIdAndCheckOutTimeIsNotNullOrderByCheckOutTimeDesc(Long employeeId);

    // Ordered queries for recent logs
    List<AttendanceLog> findByTenantIdOrderByCheckInTimeDesc(Long tenantId, Pageable pageable);
    List<AttendanceLog> findAllByOrderByCheckInTimeDesc(Pageable pageable);

    // Legacy methods
    List<AttendanceLog> findByEmployeeIdAndCheckInTimeBetween(Long employeeId,
                                                               LocalDateTime start,
                                                               LocalDateTime end);
    boolean existsByTenantIdAndEmployeeIdAndCheckInTimeAndCheckOutTimeAndDoorId(Long tenantId,
                                                                                  Long employeeId,
                                                                                  LocalDateTime checkInTime,
                                                                                  LocalDateTime checkOutTime,
                                                                                  String doorId);
    boolean existsByEmployeeIdAndCheckInTimeAndCheckOutTimeAndDoorId(Long employeeId,
                                                                      LocalDateTime checkInTime,
                                                                      LocalDateTime checkOutTime,
                                                                      String doorId);
    List<AttendanceLog> findByDeviceId(String deviceId);
    List<AttendanceLog> findByCheckInTimeBetween(LocalDateTime start, LocalDateTime end);
    long countByCheckInTimeBetween(LocalDateTime start, LocalDateTime end);
}
