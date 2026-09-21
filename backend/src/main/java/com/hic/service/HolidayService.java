package com.hic.service;

import com.hic.dto.HolidayDTO;
import com.hic.exception.ResourceNotFoundException;
import com.hic.model.Holiday;
import com.hic.model.HolidayTarget;
import com.hic.repository.HolidayRepository;
import com.hic.repository.HolidayTargetRepository;
import com.hic.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HolidayService {
    private final HolidayRepository holidayRepository;
    private final HolidayTargetRepository holidayTargetRepository;

    public List<HolidayDTO> getAll() {
        Long tenantId = TenantContext.getTenantId();
        List<Holiday> holidays = tenantId == null
                ? holidayRepository.findAll()
                : holidayRepository.findByTenantId(tenantId);
        return holidays.stream().map(this::toDTO).collect(Collectors.toList());
    }

    public HolidayDTO getById(Long id) {
        Holiday holiday = holidayRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Holiday", id));
        return toDTO(holiday);
    }

    @Transactional
    public HolidayDTO create(HolidayDTO dto) {
        Holiday holiday = toEntity(dto);
        if (holiday.getTenantId() == null) {
            Long tenantId = TenantContext.getTenantId();
            if (tenantId != null) {
                holiday.setTenantId(tenantId);
            }
        }
        Holiday saved = holidayRepository.save(holiday);
        saveTargets(saved.getId(), dto.getScopeType(), dto.getTargetIds());
        return toDTO(saved);
    }

    @Transactional
    public HolidayDTO update(Long id, HolidayDTO dto) {
        Holiday existing = holidayRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Holiday", id));
        Holiday holiday = toEntity(dto);
        holiday.setId(existing.getId());
        if (holiday.getTenantId() == null) {
            holiday.setTenantId(existing.getTenantId());
        }
        Holiday saved = holidayRepository.save(holiday);
        holidayTargetRepository.deleteByHolidayId(id);
        saveTargets(saved.getId(), dto.getScopeType(), dto.getTargetIds());
        return toDTO(saved);
    }

    @Transactional
    public void delete(Long id) {
        if (!holidayRepository.existsById(id)) {
            throw new ResourceNotFoundException("Holiday", id);
        }
        holidayTargetRepository.deleteByHolidayId(id);
        holidayRepository.deleteById(id);
    }

    public boolean isHoliday(LocalDate date) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId != null) {
            return holidayRepository.existsByTenantIdAndHolidayDate(tenantId, date);
        }
        return holidayRepository.existsByHolidayDate(date);
    }

    private void saveTargets(Long holidayId, String scopeType, List<Long> targetIds) {
        if (targetIds == null || targetIds.isEmpty() || scopeType == null) return;
        for (Long targetId : targetIds) {
            HolidayTarget ht = new HolidayTarget();
            ht.setHolidayId(holidayId);
            ht.setScopeType(scopeType);
            ht.setTargetId(targetId);
            holidayTargetRepository.save(ht);
        }
    }

    private HolidayDTO toDTO(Holiday holiday) {
        HolidayDTO dto = new HolidayDTO();
        dto.setId(holiday.getId());
        dto.setTenantId(holiday.getTenantId());
        dto.setName(holiday.getName());
        dto.setDescription(holiday.getDescription());
        dto.setHolidayDate(holiday.getHolidayDate() != null ? holiday.getHolidayDate().toString() : null);
        dto.setApplyScope(holiday.getApplyScope());
        List<HolidayTarget> targets = holidayTargetRepository.findByHolidayId(holiday.getId());
        if (!targets.isEmpty()) {
            dto.setScopeType(targets.get(0).getScopeType());
            dto.setTargetIds(targets.stream().map(HolidayTarget::getTargetId).collect(Collectors.toList()));
        } else {
            dto.setTargetIds(Collections.emptyList());
        }
        return dto;
    }

    private Holiday toEntity(HolidayDTO dto) {
        Holiday holiday = new Holiday();
        holiday.setTenantId(dto.getTenantId());
        holiday.setName(dto.getName());
        holiday.setDescription(dto.getDescription());
        if (dto.getHolidayDate() != null) {
            holiday.setHolidayDate(LocalDate.parse(dto.getHolidayDate()));
        }
        holiday.setApplyScope(dto.getApplyScope() != null ? dto.getApplyScope() : "ALL");
        return holiday;
    }
}
