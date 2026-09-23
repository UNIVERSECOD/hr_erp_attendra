package com.hic.repository;

import com.hic.model.TimetableDayRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TimetableDayRuleRepository extends JpaRepository<TimetableDayRule, Long> {
    List<TimetableDayRule> findByTimetableIdOrderByDayOfWeek(Long timetableId);

    Optional<TimetableDayRule> findByTimetableIdAndDayOfWeek(Long timetableId, Integer dayOfWeek);
}
