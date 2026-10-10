package com.hic.controller;

import com.hic.dto.ApiResponse;
import com.hic.dto.DeviceSyncDTO;
import com.hic.service.DeviceAreaService;
import com.hic.service.DeviceService;
import com.hic.service.DeviceHealthService;
import com.hic.util.AppTimeZone;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('HEAD_OFFICE_HR','OFFICE_HR','DEPARTMENT_HR')")
public class DeviceAreaController {
    private final DeviceAreaService areas;
    private final DeviceService devices;
    private final DeviceHealthService bridge;

    public record AreaRequest(Long branchId) {}
    public record HealthView(boolean available, List<DeviceSyncDTO.DeviceConfigDTO> devices) {}

    @PutMapping("/{id}/area")
    public ApiResponse<DeviceSyncDTO.DeviceConfigDTO> assign(@PathVariable Long id, @RequestBody AreaRequest request) {
        areas.assign(id, request.branchId());
        return ApiResponse.success(devices.getById(id));
    }

    /** Read bridge state only. A bridge failure must not masquerade as offline terminals. */
    @GetMapping("/health-overview")
    public ApiResponse<HealthView> health() {
        List<DeviceSyncDTO.DeviceConfigDTO> local = devices.getAll();
        if (local.isEmpty()) return ApiResponse.success(new HealthView(true, local));
        try {
            var remote = bridge.getAll().stream().collect(java.util.stream.Collectors.toMap(
                    d -> String.valueOf(d.getId()), d -> d, (a, b) -> a));
            for (var device : local) {
                var status = remote.get(device.getDeviceId());
                if (status == null) return ApiResponse.success(new HealthView(false, local));
                device.setOnline(status.isOnline());
                var lastSync = AppTimeZone.toLocalDateTime(status.getLastSyncTime());
                if (lastSync != null) device.setLastSyncTime(lastSync);
            }
            return ApiResponse.success(new HealthView(true, local));
        } catch (Exception ignored) {
            return ApiResponse.success(new HealthView(false, local));
        }
    }
}
