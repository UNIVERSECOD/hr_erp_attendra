package com.hic.repository;

import com.hic.model.HolidayTarget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HolidayTargetRepository extends JpaRepository<HolidayTarget, Long> {
    List<HolidayTarget> findByHolidayId(Long holidayId);
    void deleteByHolidayId(Long holidayId);
}
