package com.hic.scheduler;

import com.hic.model.Tenant;
import com.hic.repository.TenantRepository;
import com.hic.service.DoorAttendanceSyncService;
import com.hic.util.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceSyncSchedulerTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private DoorAttendanceSyncService doorAttendanceSyncService;

    @InjectMocks
    private AttendanceSyncScheduler scheduler;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void syncAllTenantsDevices_runsOnceUntilTenantIntervalIsDue() {
        Tenant tenant = tenant(11L, 15);
        when(tenantRepository.findAll()).thenReturn(List.of(tenant));

        scheduler.syncAllTenantsDevices();
        scheduler.syncAllTenantsDevices();

        verify(doorAttendanceSyncService, times(1))
                .syncAllDevices(any(LocalDateTime.class), any(LocalDateTime.class), eq(500));
        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    void isDue_usesConfiguredMinuteInterval() {
        LocalDateTime lastAttempt = LocalDateTime.of(2026, 9, 20, 10, 0);

        assertThat(AttendanceSyncScheduler.isDue(
                lastAttempt, 15, LocalDateTime.of(2026, 9, 20, 10, 14, 59))).isFalse();
        assertThat(AttendanceSyncScheduler.isDue(
                lastAttempt, 15, LocalDateTime.of(2026, 9, 20, 10, 15))).isTrue();
    }

    private static Tenant tenant(Long id, int intervalMinutes) {
        Tenant tenant = new Tenant();
        tenant.setId(id);
        tenant.setCompanyName("Test tenant");
        tenant.setAttendanceSyncIntervalMinutes(intervalMinutes);
        return tenant;
    }
}
