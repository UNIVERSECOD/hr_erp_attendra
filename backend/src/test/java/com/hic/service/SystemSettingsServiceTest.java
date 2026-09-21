package com.hic.service;

import com.hic.dto.SystemSettingsResponse;
import com.hic.exception.BadRequestException;
import com.hic.model.Tenant;
import com.hic.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemSettingsServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @InjectMocks
    private SystemSettingsService systemSettingsService;

    private Tenant tenant;

    @BeforeEach
    void setUp() {
        tenant = new Tenant();
        tenant.setId(7L);
        tenant.setAttendanceSyncIntervalMinutes(2);
    }

    @Test
    void getSettings_returnsCurrentTenantInterval() {
        when(tenantRepository.findById(7L)).thenReturn(Optional.of(tenant));

        SystemSettingsResponse response = systemSettingsService.getSettings(7L);

        assertThat(response.syncIntervalMinutes()).isEqualTo(2);
    }

    @Test
    void updateSettings_validInterval_persistsForCurrentTenant() {
        when(tenantRepository.findById(7L)).thenReturn(Optional.of(tenant));
        when(tenantRepository.save(tenant)).thenReturn(tenant);

        SystemSettingsResponse response = systemSettingsService.updateSettings(7L, 15);

        assertThat(response.syncIntervalMinutes()).isEqualTo(15);
        assertThat(tenant.getAttendanceSyncIntervalMinutes()).isEqualTo(15);
        verify(tenantRepository).save(tenant);
    }

    @Test
    void updateSettings_unsupportedInterval_isRejected() {
        assertThatThrownBy(() -> systemSettingsService.updateSettings(7L, 3))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("2, 5, 10, 15, 30");

        verify(tenantRepository, never()).findById(7L);
        verify(tenantRepository, never()).save(tenant);
    }

    @Test
    void getSettings_missingTenantContext_isRejected() {
        assertThatThrownBy(() -> systemSettingsService.getSettings(null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Şirkət");
    }
}
