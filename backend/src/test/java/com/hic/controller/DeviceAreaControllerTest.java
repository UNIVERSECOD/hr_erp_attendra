package com.hic.controller;

import com.hic.dto.DeviceSyncDTO;
import com.hic.service.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class DeviceAreaControllerTest {
    @Test void healthUsesBridgeIdAndExcludesOtherDevices() {
        var service = mock(DeviceService.class);
        var bridge = mock(DeviceHealthService.class);
        var local = new DeviceSyncDTO.DeviceConfigDTO(); local.setId(7L); local.setDeviceId("900");
        var remote = new DeviceSyncDTO.IsapiDeviceResponse(); remote.setId(900L); remote.setOnline(true);
        var other = new DeviceSyncDTO.IsapiDeviceResponse(); other.setId(7L); other.setOnline(false);
        when(service.getAll()).thenReturn(List.of(local)); when(bridge.getAll()).thenReturn(List.of(other, remote));
        var view = new DeviceAreaController(mock(DeviceAreaService.class), service, bridge).health().getData();
        assertThat(view.available()).isTrue(); assertThat(view.devices()).hasSize(1); assertThat(view.devices().get(0).isOnline()).isTrue();
    }
    @Test void missingBridgeDataDoesNotClaimOfflineOrRecovery() {
        var service = mock(DeviceService.class); var bridge = mock(DeviceHealthService.class);
        var local = new DeviceSyncDTO.DeviceConfigDTO(); local.setDeviceId("900");
        when(service.getAll()).thenReturn(List.of(local)); when(bridge.getAll()).thenThrow(new RuntimeException());
        assertThat(new DeviceAreaController(mock(DeviceAreaService.class), service, bridge).health().getData().available()).isFalse();
    }
}
