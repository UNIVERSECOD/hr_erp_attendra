package com.hic.service;

import com.hic.exception.BadRequestException;
import com.hic.exception.ResourceNotFoundException;
import com.hic.model.Timetable;
import com.hic.model.TimetableDayRule;
import com.hic.repository.TimetableRepository;
import com.hic.util.ShiftTypes;
import com.hic.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        validateTimetable(timetable);
        applyDayRules(timetable, timetable.getDayRules(), true);
        normalizeFlexibleShift(timetable);
        synchronizeLegacyFields(timetable);
        return timetableRepository.save(timetable);
    }

    @Transactional
    public Timetable update(Long id, Timetable timetable) {
        Timetable existing = getById(id);
        validateTimetable(timetable);

        existing.setName(timetable.getName());
        existing.setDescription(timetable.getDescription());
        existing.setStartTime(timetable.getStartTime());
        existing.setEndTime(timetable.getEndTime());
        existing.setCrossesMidnight(timetable.getCrossesMidnight());
        existing.setAllowedLateMinutes(timetable.getAllowedLateMinutes());
        existing.setAllowedEarlyLeaveMinutes(timetable.getAllowedEarlyLeaveMinutes());
        existing.setShiftType(timetable.getShiftType());
        existing.setBreakMinutes(timetable.getBreakMinutes());

        if (timetable.getDayRules() != null && !timetable.getDayRules().isEmpty()) {
            applyDayRules(existing, timetable.getDayRules(), false);
        } else if (existing.getDayRules() == null || existing.getDayRules().isEmpty()) {
            applyDayRules(existing, List.of(), true);
        }

        normalizeFlexibleShift(existing);
        synchronizeLegacyFields(existing);
        return timetableRepository.save(existing);
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

    private void validateTimetable(Timetable timetable) {
        if (timetable == null) {
            throw new BadRequestException("Timetable is required");
        }
        if (timetable.getName() == null || timetable.getName().isBlank()) {
            throw new BadRequestException("Timetable name is required");
        }
        if (timetable.getShiftType() == null || timetable.getShiftType().isBlank()) {
            throw new BadRequestException("Shift type is required");
        }
    }

    /**
     * Applies an ISO Monday-Sunday rule set while retaining entity identities on update.
     * Empty rules are accepted only for legacy clients and expanded from the old fields.
     */
    private void applyDayRules(Timetable timetable, List<TimetableDayRule> requestedRules, boolean allowLegacyDefaults) {
        List<TimetableDayRule> source = requestedRules;
        if (source == null || source.isEmpty()) {
            if (!allowLegacyDefaults) {
                return;
            }
            source = buildLegacyRules(timetable);
        }

        validateDayRules(source);
        if (timetable.getDayRules() == null) {
            timetable.setDayRules(new java.util.ArrayList<>());
        }

        Map<Integer, TimetableDayRule> existingByDay = new HashMap<>();
        for (TimetableDayRule existing : timetable.getDayRules()) {
            if (existing.getDayOfWeek() != null) {
                existingByDay.put(existing.getDayOfWeek(), existing);
            }
        }

        Set<Integer> requestedDays = new HashSet<>();
        for (TimetableDayRule input : source) {
            Integer day = input.getDayOfWeek();
            requestedDays.add(day);
            TimetableDayRule target = existingByDay.get(day);
            if (target == null) {
                target = new TimetableDayRule();
                target.setDayOfWeek(day);
                timetable.getDayRules().add(target);
            }
            target.setTimetable(timetable);
            copyRuleValues(input, target);
        }
        timetable.getDayRules().removeIf(rule -> !requestedDays.contains(rule.getDayOfWeek()));
        timetable.getDayRules().sort(java.util.Comparator.comparing(TimetableDayRule::getDayOfWeek));
    }

    private List<TimetableDayRule> buildLegacyRules(Timetable timetable) {
        LocalTime start = timetable.getStartTime() != null ? timetable.getStartTime() : LocalTime.of(9, 0);
        LocalTime end = timetable.getEndTime() != null ? timetable.getEndTime() : LocalTime.of(18, 0);
        int breakMinutes = nonNegative(timetable.getBreakMinutes());
        int lateMinutes = nonNegative(timetable.getAllowedLateMinutes());
        int earlyMinutes = nonNegative(timetable.getAllowedEarlyLeaveMinutes());

        java.util.ArrayList<TimetableDayRule> rules = new java.util.ArrayList<>();
        for (int day = 1; day <= 7; day++) {
            TimetableDayRule rule = new TimetableDayRule();
            rule.setDayOfWeek(day);
            rule.setWorkingDay(true);
            rule.setStartTime(start);
            rule.setEndTime(end);
            rule.setBreakMinutes(breakMinutes);
            rule.setAllowedLateMinutes(lateMinutes);
            rule.setAllowedEarlyLeaveMinutes(earlyMinutes);
            rules.add(rule);
        }
        return rules;
    }

    private void validateDayRules(List<TimetableDayRule> rules) {
        if (rules.size() != 7) {
            throw new BadRequestException("A timetable must contain exactly seven day rules");
        }
        Set<Integer> days = new HashSet<>();
        boolean hasWorkingDay = false;
        for (TimetableDayRule rule : rules) {
            if (rule == null || rule.getDayOfWeek() == null
                    || rule.getDayOfWeek() < 1 || rule.getDayOfWeek() > 7
                    || !days.add(rule.getDayOfWeek())) {
                throw new BadRequestException("Timetable day rules must contain unique ISO days 1 through 7");
            }
            validateMinuteValue(rule.getBreakMinutes(), "Break minutes");
            validateMinuteValue(rule.getAllowedLateMinutes(), "Allowed late minutes");
            validateMinuteValue(rule.getAllowedEarlyLeaveMinutes(), "Allowed early leave minutes");
            if (Boolean.TRUE.equals(rule.getWorkingDay())) {
                hasWorkingDay = true;
                if (rule.getStartTime() == null || rule.getEndTime() == null) {
                    throw new BadRequestException("Working days require start and end times");
                }
                if (rule.getStartTime().equals(rule.getEndTime())) {
                    throw new BadRequestException("Working day start and end times must be different");
                }
            }
        }
        if (!hasWorkingDay) {
            throw new BadRequestException("A timetable must contain at least one working day");
        }
    }

    private void validateMinuteValue(Integer value, String label) {
        if (value != null && (value < 0 || value > 1440)) {
            throw new BadRequestException(label + " must be between 0 and 1440");
        }
    }

    private void copyRuleValues(TimetableDayRule source, TimetableDayRule target) {
        target.setDayOfWeek(source.getDayOfWeek());
        target.setWorkingDay(Boolean.TRUE.equals(source.getWorkingDay()));
        target.setStartTime(source.getStartTime());
        target.setEndTime(source.getEndTime());
        target.setBreakMinutes(nonNegative(source.getBreakMinutes()));
        target.setAllowedLateMinutes(nonNegative(source.getAllowedLateMinutes()));
        target.setAllowedEarlyLeaveMinutes(nonNegative(source.getAllowedEarlyLeaveMinutes()));
    }

    /** Keeps old clients and fallback calculations aligned with the first active day. */
    private void synchronizeLegacyFields(Timetable timetable) {
        if (ShiftTypes.isFlexible(timetable.getShiftType())) {
            return;
        }
        timetable.getDayRules().stream()
                .filter(rule -> Boolean.TRUE.equals(rule.getWorkingDay()))
                .findFirst()
                .ifPresent(rule -> {
                    timetable.setStartTime(rule.getStartTime());
                    timetable.setEndTime(rule.getEndTime());
                    timetable.setBreakMinutes(nonNegative(rule.getBreakMinutes()));
                    timetable.setAllowedLateMinutes(nonNegative(rule.getAllowedLateMinutes()));
                    timetable.setAllowedEarlyLeaveMinutes(nonNegative(rule.getAllowedEarlyLeaveMinutes()));
                    timetable.setCrossesMidnight(!rule.getEndTime().isAfter(rule.getStartTime()));
                });
    }

    private int nonNegative(Integer value) {
        return value != null && value > 0 ? value : 0;
    }
}
