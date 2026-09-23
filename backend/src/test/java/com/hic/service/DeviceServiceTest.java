package com.hic.service;

import com.hic.dto.DeviceSyncDTO;
import com.hic.model.DeviceConfig;
import com.hic.repository.DeviceConfigRepository;
import com.hic.repository.DoorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    @Mock
    private DeviceConfigRepository deviceConfigRepository;
    @Mock
    private DeviceSyncService deviceSyncService;
    @Mock
    private DoorRepository doorRepository;

    @InjectMocks
    private DeviceService deviceService;

    @Test
    void syncFromIsapi_offlineResponsePreservesLastSuccessfulSync() {
        LocalDateTime lastSuccessfulSync = LocalDateTime.of(2026, 9, 23, 9, 45);
        DeviceConfig existing = new DeviceConfig();
        existing.setDeviceId("1");
        existing.setDeviceName("Main Gate");
        existing.setDeviceIp("192.168.0.110");
        existing.setStatus("ACTIVE");
        existing.setOnline(true);
        existing.setLastSyncTime(lastSuccessfulSync);

        DeviceSyncDTO.DeviceConfigDTO upstream = new DeviceSyncDTO.DeviceConfigDTO();
        upstream.setDeviceId("1");
        upstream.setDeviceName("Main Gate");
        upstream.setDeviceIp("192.168.0.115");
        upstream.setStatus("ACTIVE");
        upstream.setOnline(false);

        when(deviceSyncService.getAllDevices(null)).thenReturn(List.of(upstream));
        when(deviceConfigRepository.findByDeviceId("1")).thenReturn(Optional.of(existing));

        DeviceSyncDTO.SyncResultDTO result = deviceService.syncFromIsapi();

        assertThat(result.isSuccess()).isTrue();
        assertThat(existing.isOnline()).isFalse();
        assertThat(existing.getLastSyncTime()).isEqualTo(lastSuccessfulSync);
        verify(deviceConfigRepository).save(existing);
    }
}
