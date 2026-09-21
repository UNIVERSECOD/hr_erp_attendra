package com.hic.repository;

import com.hic.model.DailyAttendanceSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DailyAttendanceSummaryRepository extends JpaRepository<DailyAttendanceSummary, Long> {
    List<DailyAttendanceSummary> findByEmployeeIdAndAttendanceDateBetween(Long employeeId,
                                                                           LocalDate start,
                                                                           LocalDate end);
    List<DailyAttendanceSummary> findByAttendanceDateBetween(LocalDate start, LocalDate end);
    Optional<DailyAttendanceSummary> findByEmployeeIdAndAttendanceDate(Long employeeId, LocalDate date);
}
