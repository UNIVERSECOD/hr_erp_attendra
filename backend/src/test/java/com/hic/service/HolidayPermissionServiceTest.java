package com.hic.service;

import com.hic.dto.HolidayPermissionDTO;
import com.hic.model.HolidayPermission;
import com.hic.repository.HolidayPermissionRepository;
import com.hic.util.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HolidayPermissionServiceTest {

    @Mock
    private HolidayPermissionRepository holidayPermissionRepository;

    @InjectMocks
    private HolidayPermissionService holidayPermissionService;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void create_overlappingCompanyHoliday_throwsIllegalArgument() {
        HolidayPermissionDTO dto = new HolidayPermissionDTO();
        dto.setName("Novruz");
        dto.setStartDate(LocalDate.of(2026, 3, 20));
        dto.setEndDate(LocalDate.of(2026, 3, 23));
        dto.setApplyScope("COMPANY");
        dto.setStatus("ACTIVE");

        HolidayPermission existing = new HolidayPermission();
        existing.setId(10L);
        existing.setApplyScope("COMPANY");
        existing.setStatus("ACTIVE");

        TenantContext.setTenantId(1L);
        TenantContext.setUserId(99L);
        when(holidayPermissionRepository.findOverlapping(1L, dto.getStartDate(), dto.getEndDate()))
                .thenReturn(List.of(existing));

        assertThrows(IllegalArgumentException.class, () -> holidayPermissionService.create(dto));
        verify(holidayPermissionRepository, never()).save(any(HolidayPermission.class));
    }

    @Test
    void isHoliday_departmentScopeMatch_returnsTrue() {
        HolidayPermission permission = new HolidayPermission();
        permission.setApplyScope("DEPARTMENT");
        permission.setStatus("ACTIVE");
        permission.setTargetIds(new Long[]{5L, 9L});

        LocalDate date = LocalDate.of(2026, 1, 1);
        TenantContext.setTenantId(1L);
        when(holidayPermissionRepository.findOverlapping(1L, date, date)).thenReturn(List.of(permission));

        assertTrue(holidayPermissionService.isHoliday(date, null, 9L, null));
    }
}
