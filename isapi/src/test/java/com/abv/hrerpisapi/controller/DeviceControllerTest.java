package com.abv.hrerpisapi.controller;

import com.abv.hrerpisapi.dao.entity.DeviceCursorEntity;
import com.abv.hrerpisapi.dao.entity.DeviceEntity;
import com.abv.hrerpisapi.dao.repository.DeviceCursorRepository;
import com.abv.hrerpisapi.dao.repository.DeviceRepository;
import com.abv.hrerpisapi.device.client.IsapiClient;
import com.abv.hrerpisapi.scheduler.AcsEventHistoryPoller;
import com.abv.hrerpisapi.service.DeviceCursorService;
import com.abv.hrerpisapi.service.DeviceWorkerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceControllerTest {

    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private DeviceCursorRepository deviceCursorRepository;
    @Mock
    private DeviceCursorService deviceCursorService;
    @Mock
    private DeviceWorkerService deviceWorkerService;
    @Mock
    private IsapiClient isapiClient;
    @Mock
    private AcsEventHistoryPoller historyPoller;

    @InjectMocks
    private DeviceController controller;

    @Test
    void resetCursor_resetsLastSerialNoAndLastEventTime() {
        DeviceEntity device = new DeviceEntity();
        device.setId(1L);

        DeviceCursorEntity existingCursor = new DeviceCursorEntity();
        existingCursor.setDeviceId(1L);
        existingCursor.setLastSerialNo(0L);
        existingCursor.setLastEventTime(null);

        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        when(deviceCursorService.resetCursor(1L)).thenReturn(existingCursor);

        DeviceController.CursorResetResponse response = controller.resetCursor(1L);

        verify(deviceCursorService).resetCursor(1L);
        assertThat(response.deviceId()).isEqualTo(1L);
        assertThat(response.lastSerialNo()).isEqualTo(0L);
        assertThat(response.lastEventTime()).isNull();
    }

    @Test
    void list_prefersLastSuccessfulPollTime() {
        DeviceEntity device = new DeviceEntity();
        device.setId(1L);
        device.setIp("192.168.1.10");
        device.setUsername("admin");
        device.setName("Front Door");
        device.setEnabled(true);

        DeviceCursorEntity cursor = new DeviceCursorEntity();
        cursor.setDeviceId(1L);
        cursor.setLastEventTime(OffsetDateTime.parse("2026-05-18T10:15:30Z"));
        OffsetDateTime lastPollTime = OffsetDateTime.parse("2026-05-18T10:20:30Z");
        cursor.setLastPollTime(lastPollTime);

        when(deviceRepository.findAll()).thenReturn(List.of(device));
        when(deviceCursorRepository.findAllById(List.of(1L))).thenReturn(List.of(cursor));
        when(deviceWorkerService.isRunning(1L)).thenReturn(true);

        List<DeviceController.DeviceResponse> response = controller.list(null);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).lastSyncTime()).isEqualTo(lastPollTime);
    }

    @Test
    void sync_pollsDeviceHistoryImmediately() throws Exception {
        DeviceEntity device = new DeviceEntity();
        device.setId(1L);

        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        when(historyPoller.pollDevice(device)).thenReturn(3);

        DeviceController.DeviceSyncResponse response = controller.sync(1L);

        verify(deviceWorkerService).startDevice(device);
        verify(historyPoller).pollDevice(device);
        assertThat(response.success()).isTrue();
        assertThat(response.recordsSynced()).isEqualTo(3);
    }
}
