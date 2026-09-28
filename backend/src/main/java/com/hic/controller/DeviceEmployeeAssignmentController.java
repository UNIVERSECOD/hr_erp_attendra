package com.hic.controller;

import com.hic.dto.ApiResponse;
import com.hic.dto.DeviceEmployeeAssignmentDTO.AssignmentView;
import com.hic.dto.DeviceEmployeeAssignmentDTO.SyncResult;
import com.hic.dto.DeviceEmployeeAssignmentDTO.UpdateRequest;
import com.hic.service.DeviceEmployeeAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/devices/{deviceConfigId}/employees")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('HEAD_OFFICE_HR','OFFICE_HR','DEPARTMENT_HR')")
public class DeviceEmployeeAssignmentController {

    private final DeviceEmployeeAssignmentService deviceEmployeeAssignmentService;

    @GetMapping
    public ResponseEntity<ApiResponse<AssignmentView>> getAssignments(@PathVariable Long deviceConfigId) {
        return ResponseEntity.ok(ApiResponse.success(
                deviceEmployeeAssignmentService.getAssignments(deviceConfigId)));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<AssignmentView>> updateAssignments(
            @PathVariable Long deviceConfigId,
            @RequestBody UpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                deviceEmployeeAssignmentService.updateManualAssignments(deviceConfigId, request)));
    }

    @PostMapping("/sync")
    public ResponseEntity<ApiResponse<SyncResult>> syncEmployees(@PathVariable Long deviceConfigId) {
        return ResponseEntity.ok(ApiResponse.success(
                deviceEmployeeAssignmentService.syncEmployees(deviceConfigId)));
    }
}
