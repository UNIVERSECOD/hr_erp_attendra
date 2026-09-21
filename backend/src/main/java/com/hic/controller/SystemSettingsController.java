package com.hic.controller;

import com.hic.dto.ApiResponse;
import com.hic.dto.SystemSettingsResponse;
import com.hic.dto.UpdateSystemSettingsRequest;
import com.hic.service.SystemSettingsService;
import com.hic.util.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings/system")
@RequiredArgsConstructor
@PreAuthorize("hasRole('HEAD_OFFICE_HR')")
public class SystemSettingsController {

    private final SystemSettingsService systemSettingsService;

    @GetMapping
    public ResponseEntity<ApiResponse<SystemSettingsResponse>> getSettings() {
        return ResponseEntity.ok(ApiResponse.success(
                systemSettingsService.getSettings(TenantContext.getTenantId())));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<SystemSettingsResponse>> updateSettings(
            @Valid @RequestBody UpdateSystemSettingsRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Sistem parametrləri yadda saxlanıldı",
                systemSettingsService.updateSettings(
                        TenantContext.getTenantId(), request.getSyncIntervalMinutes())));
    }
}
