package com.hic.service;

import com.hic.exception.ResourceNotFoundException;
import com.hic.model.Timetable;
import com.hic.repository.TimetableRepository;
import com.hic.util.ShiftTypes;
import com.hic.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TimetableService {
    private final TimetableRepository timetableRepository;

    public List<Timetable> getAll() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return timetableRepository.findAll();
        }
        return timetableRepository.findByTenantId(tenantId);
    }

    public Timetable getById(Long id) {
        return timetableRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Timetable", id));
    }

    @Transactional
    public Timetable create(Timetable timetable) {
        if (timetable.getTenantId() == null) {
            Long tenantId = TenantContext.getTenantId();
            if (tenantId != null) {
                timetable.setTenantId(tenantId);
            }
        }
        normalizeFlexibleShift(timetable);
        return timetableRepository.save(timetable);
    }

    @Transactional
    public Timetable update(Long id, Timetable timetable) {
        Timetable existing = getById(id);
        timetable.setId(existing.getId());
        if (timetable.getTenantId() == null) {
            timetable.setTenantId(existing.getTenantId());
        }
        normalizeFlexibleShift(timetable);
        return timetableRepository.save(timetable);
    }

    @Transactional
    public void delete(Long id) {
        if (!timetableRepository.existsById(id)) {
            throw new ResourceNotFoundException("Timetable", id);
        }
        timetableRepository.deleteById(id);
    }

    /**
     * Flexible shifts have no fixed hours or late grace — clear schedule constraints.
     * DB columns remain NOT NULL so we store full-day placeholders.
     */
    private void normalizeFlexibleShift(Timetable timetable) {
        if (!ShiftTypes.isFlexible(timetable.getShiftType())) {
            return;
        }
        timetable.setStartTime(LocalTime.MIDNIGHT);
        timetable.setEndTime(LocalTime.of(23, 59));
        timetable.setAllowedLateMinutes(0);
        timetable.setAllowedEarlyLeaveMinutes(0);
        timetable.setCrossesMidnight(false);
    }
}
