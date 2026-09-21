package com.hic.repository;

import com.hic.model.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface HolidayRepository extends JpaRepository<Holiday, Long> {
    List<Holiday> findByTenantId(Long tenantId);
    List<Holiday> findByTenantIdAndHolidayDateBetween(Long tenantId, LocalDate start, LocalDate end);
    List<Holiday> findByHolidayDateBetween(LocalDate start, LocalDate end);
    boolean existsByTenantIdAndHolidayDate(Long tenantId, LocalDate holidayDate);
    boolean existsByHolidayDate(LocalDate holidayDate);
}
