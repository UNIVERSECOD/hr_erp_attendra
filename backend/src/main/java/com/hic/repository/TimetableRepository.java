package com.hic.repository;

import com.hic.model.Timetable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TimetableRepository extends JpaRepository<Timetable, Long> {
    List<Timetable> findByTenantId(Long tenantId);
    Optional<Timetable> findByTenantIdAndId(Long tenantId, Long id);
}
