package com.abv.hrerpisapi.controller;

import com.abv.hrerpisapi.service.DeviceUserService;
import com.abv.hrerpisapi.service.DeviceUserService.DeviceFaceFromDevice;
import com.abv.hrerpisapi.service.DeviceUserService.DeviceUserFaceSyncResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Live face-library pull from Hikvision devices (FDSearch / FDSetUp).
 * Used by HR employee import to associate device face images with employee profiles.
 */
@RestController
@Slf4j
@RequestMapping("/api/devices/{deviceId}/faces")
@RequiredArgsConstructor
public class DeviceFaceController {

    private final DeviceUserService deviceUserService;

    /**
     * Pages through all FDLib records on the device. Each record's FPID is the person ID
     * (UserInfo.employeeNo) used when faces are enrolled on Access Control terminals.
     */
    @GetMapping("/from-device")
    public List<DeviceFaceFromDevice> listFacesFromDevice(@PathVariable Long deviceId) {
        return deviceUserService.fetchFacesFromDevice(deviceId);
    }

    /**
     * Downloads the face image for a person ID. Does not require a local device_users row.
     */
    @GetMapping("/{employeeNo}/download")
    public DeviceUserFaceSyncResponse downloadFaceByEmployeeNo(@PathVariable Long deviceId,
                                                               @PathVariable String employeeNo) {
        return deviceUserService.downloadFaceByEmployeeNo(deviceId, employeeNo);
    }
}
