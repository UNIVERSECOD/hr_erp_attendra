package com.hic.controller;

import com.hic.dto.ApiResponse;
import com.hic.dto.DeviceEmployeeImportDTO;
import com.hic.service.HikDeviceUserImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Setup-team endpoints for onboarding an existing Hikvision site into this ERP.
 * Restricted to central-office setup accounts.
 */
@RestController
@RequestMapping("/api/setup")
@RequiredArgsConstructor
@PreAuthorize("hasRole('HEAD_OFFICE_HR')")
public class SetupImportController {

    private final HikDeviceUserImportService hikDeviceUserImportService;

    /**
     * Import employees from all (or selected) devices of a branch.
     *
     * <p>POST /api/setup/import-employees
     * Body: {@code { "branchId": 1, "deviceConfigIds": [10, 11] }} —
     * {@code deviceConfigIds} optional; when omitted, all branch devices are scanned.
     */
    @PostMapping("/import-employees")
    public ResponseEntity<ApiResponse<DeviceEmployeeImportDTO.ImportResult>> importEmployees(
            @RequestBody DeviceEmployeeImportDTO.ImportRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                hikDeviceUserImportService.importUsersFromBranch(request)));
    }
}
