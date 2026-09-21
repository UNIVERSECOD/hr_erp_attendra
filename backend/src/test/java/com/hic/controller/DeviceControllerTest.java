package com.hic.controller;

import com.hic.dto.ApiResponse;
import com.hic.dto.DeviceSyncDTO;
import com.hic.model.DeviceConfig;
import com.hic.repository.DeviceConfigRepository;
import com.hic.repository.UserRepository;
import com.hic.service.DeviceService;
import com.hic.service.DeviceSyncService;
import com.hic.service.DoorAttendanceSyncService;
import com.hic.service.HikDeviceUserImportService;
import com.hic.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = DeviceController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class}
)
class DeviceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DeviceService deviceService;

    @MockBean
    private DeviceSyncService deviceSyncService;

    @MockBean
    private DeviceConfigRepository deviceConfigRepository;

    @MockBean
    private HikDeviceUserImportService hikDeviceUserImportService;

    @MockBean
    private DoorAttendanceSyncService doorAttendanceSyncService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private UserRepository userRepository;

    private DeviceConfig backendDevice() {
        DeviceConfig device = new DeviceConfig();
        device.setId(1L);
        device.setDeviceId("10");
        device.setDeviceName("Front Door");
        device.setStatus("ACTIVE");
        return device;
    }

    private DeviceSyncDTO.DeviceConfigDTO deviceConfigDto() {
        return new DeviceSyncDTO.DeviceConfigDTO(
                1L, "10", "Front Door", "10.0.0.1", 80, "admin", null, null, null, null, "ACTIVE", null);
    }

    @Test
    void getAll_returns200() throws Exception {
        when(deviceService.getAll()).thenReturn(List.of(deviceConfigDto()));

        mockMvc.perform(get("/api/devices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].deviceName").value("Front Door"));

        verify(deviceService).syncFromIsapi();
        verify(deviceService).getAll();
    }

    @Test
    void patchEnabled_returns200() throws Exception {
        DeviceConfig backendDevice = backendDevice();
        DeviceSyncDTO.DeviceConfigDTO updated = deviceConfigDto();
        updated.setStatus("INACTIVE");

        when(deviceConfigRepository.findById(1L)).thenReturn(Optional.of(backendDevice));
        when(deviceSyncService.updateEnabled(eq(10L), any(DeviceSyncDTO.DeviceEnabledDTO.class))).thenReturn(updated);
        when(deviceService.getById(1L)).thenReturn(updated);

        mockMvc.perform(patch("/api/devices/1/enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INACTIVE"));

        verify(deviceConfigRepository).save(backendDevice);
    }

    @Test
    void start_returns200() throws Exception {
        when(deviceConfigRepository.findById(1L)).thenReturn(Optional.of(backendDevice()));
        when(deviceSyncService.startDevice(10L))
                .thenReturn(new DeviceSyncDTO.DeviceRuntimeDTO(10L, true, true, "RUNNING"));

        mockMvc.perform(post("/api/devices/1/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.running").value(true));
    }

    @Test
    void status_returns200() throws Exception {
        when(deviceConfigRepository.findById(1L)).thenReturn(Optional.of(backendDevice()));
        when(deviceSyncService.getStatus(10L))
                .thenReturn(new DeviceSyncDTO.DeviceStatusDTO(10L, true, 200, "OK"));

        mockMvc.perform(get("/api/devices/1/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.online").value(true));
    }

    @Test
    void sync_returns200() throws Exception {
        when(deviceConfigRepository.findById(1L)).thenReturn(Optional.of(backendDevice()));
        when(deviceSyncService.syncDevice(10L))
                .thenReturn(new DeviceSyncDTO.SyncResultDTO(true, "Synced", 3));

        mockMvc.perform(post("/api/devices/1/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.recordsSynced").value(3));

        verify(doorAttendanceSyncService).syncAllDevices(any(), any(), eq(500));
    }
}
