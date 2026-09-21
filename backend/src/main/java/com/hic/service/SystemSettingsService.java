package com.hic.service;

import com.hic.dto.SystemSettingsResponse;
import com.hic.exception.BadRequestException;
import com.hic.exception.ResourceNotFoundException;
import com.hic.model.Tenant;
import com.hic.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class SystemSettingsService {

    private static final Set<Integer> ALLOWED_SYNC_INTERVALS = Set.of(2, 5, 10, 15, 30, 60);

    private final TenantRepository tenantRepository;

    @Transactional(readOnly = true)
    public SystemSettingsResponse getSettings(Long tenantId) {
        Tenant tenant = getTenant(tenantId);
        return toResponse(tenant);
    }

    @Transactional
    public SystemSettingsResponse updateSettings(Long tenantId, Integer syncIntervalMinutes) {
        if (syncIntervalMinutes == null || !ALLOWED_SYNC_INTERVALS.contains(syncIntervalMinutes)) {
            throw new BadRequestException("Sinxronlaşdırma aralığı 2, 5, 10, 15, 30 və ya 60 dəqiqə olmalıdır");
        }

        Tenant tenant = getTenant(tenantId);
        tenant.setAttendanceSyncIntervalMinutes(syncIntervalMinutes);
        return toResponse(tenantRepository.save(tenant));
    }

    private Tenant getTenant(Long tenantId) {
        if (tenantId == null) {
            throw new BadRequestException("Şirkət məlumatı müəyyən edilə bilmədi");
        }
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));
    }

    private SystemSettingsResponse toResponse(Tenant tenant) {
        int interval = tenant.getAttendanceSyncIntervalMinutes() != null
                ? tenant.getAttendanceSyncIntervalMinutes()
                : Tenant.DEFAULT_ATTENDANCE_SYNC_INTERVAL_MINUTES;
        return new SystemSettingsResponse(interval);
    }
}
