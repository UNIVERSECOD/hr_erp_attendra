package com.hic.repository;

import com.hic.model.WorkSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkScheduleRepository extends JpaRepository<WorkSchedule, Long> {
    List<WorkSchedule> findByEmployeeId(Long employeeId);
    List<WorkSchedule> findByEmployeeIdAndEffectiveDateLessThanEqual(Long employeeId, LocalDate date);
    Optional<WorkSchedule> findTopByEmployeeIdAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
            Long employeeId, LocalDate date);
}
