package com.hic.scheduler;

import com.hic.model.Tenant;
import com.hic.repository.TenantRepository;
import com.hic.service.DoorAttendanceSyncService;
import com.hic.util.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class AttendanceSyncScheduler {

    private final TenantRepository tenantRepository;
    private final DoorAttendanceSyncService doorAttendanceSyncService;
    private final Map<Long, LocalDateTime> lastSyncAttempts = new ConcurrentHashMap<>();

    // Check once per minute; each tenant keeps its own configured sync interval.
    @Scheduled(fixedDelayString = "${attendance.sync.scheduler-check-ms:60000}")
    public void syncAllTenantsDevices() {
        List<Tenant> tenants = tenantRepository.findAll();
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusDays(3);

        for (Tenant tenant : tenants) {
            int intervalMinutes = syncIntervalMinutes(tenant);
            LocalDateTime lastAttempt = lastSyncAttempts.get(tenant.getId());
            if (!isDue(lastAttempt, intervalMinutes, end)) {
                continue;
            }

            lastSyncAttempts.put(tenant.getId(), end);
            try {
                TenantContext.setTenantId(tenant.getId());
                log.info("Syncing attendance devices for tenant {} ({}) at {} minute interval",
                        tenant.getCompanyName(), tenant.getId(), intervalMinutes);
                doorAttendanceSyncService.syncAllDevices(start, end, 500);
            } catch (Exception e) {
                log.error("Failed to sync attendance for tenant id: " + tenant.getId(), e);
            } finally {
                TenantContext.clear();
            }
        }
    }

    static boolean isDue(LocalDateTime lastAttempt, int intervalMinutes, LocalDateTime now) {
        return lastAttempt == null || !now.isBefore(lastAttempt.plusMinutes(intervalMinutes));
    }

    private int syncIntervalMinutes(Tenant tenant) {
        Integer configuredInterval = tenant.getAttendanceSyncIntervalMinutes();
        return configuredInterval != null && configuredInterval > 0
                ? configuredInterval
                : Tenant.DEFAULT_ATTENDANCE_SYNC_INTERVAL_MINUTES;
    }
}
