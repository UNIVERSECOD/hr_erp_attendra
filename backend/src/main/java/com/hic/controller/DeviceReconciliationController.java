package com.hic.controller;

import com.hic.dto.ApiResponse;
import com.hic.dto.DeviceReconciliationDTO.*;
import com.hic.service.DeviceReconciliationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/devices/{deviceConfigId}/employees/reconciliation")
@RequiredArgsConstructor
@PreAuthorize("hasRole('HEAD_OFFICE_HR')")
public class DeviceReconciliationController {
    private final DeviceReconciliationService service;
    @PostMapping("/preview")
    public ApiResponse<Preview> preview(@PathVariable Long deviceConfigId) {
        return ApiResponse.success(service.preview(deviceConfigId));
    }
    @PostMapping("/apply")
    public ApiResponse<Progress> apply(@PathVariable Long deviceConfigId, @RequestBody ApplyRequest request) {
        return ApiResponse.success(service.apply(deviceConfigId, request));
    }
}
