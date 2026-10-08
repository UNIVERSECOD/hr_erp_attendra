package com.hic.controller;

import com.hic.dto.ApiResponse;
import com.hic.dto.BackupSettingsResponse;
import com.hic.dto.UpdateBackupSettingsRequest;
import com.hic.service.BackupSettingsService;
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
@RequestMapping("/api/settings/backup")
@RequiredArgsConstructor
@PreAuthorize("hasRole('HEAD_OFFICE_HR')")
public class BackupSettingsController {

    private final BackupSettingsService backupSettingsService;

    @GetMapping
    public ResponseEntity<ApiResponse<BackupSettingsResponse>> getSettings() {
        return ResponseEntity.ok(ApiResponse.success(backupSettingsService.getSettings()));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<BackupSettingsResponse>> updateSettings(
            @Valid @RequestBody UpdateBackupSettingsRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Backup parametrləri yadda saxlanıldı",
                backupSettingsService.updateSettings(request.enabled(), request.folderPath())));
    }
}
