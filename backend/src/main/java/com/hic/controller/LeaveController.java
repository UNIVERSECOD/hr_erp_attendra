package com.hic.controller;

import com.hic.dto.LeaveRequestDTO;
import com.hic.dto.ApiResponse;
import com.hic.model.LeaveRequest.LeaveStatus;
import com.hic.model.LeaveType;
import com.hic.service.LeaveService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/leaves")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('HEAD_OFFICE_HR','OFFICE_HR','DEPARTMENT_HR')")
public class LeaveController {
    private final LeaveService leaveService;

    @GetMapping("/types")
    public ResponseEntity<ApiResponse<List<LeaveType>>> getLeaveTypes() {
        return ResponseEntity.ok(ApiResponse.success(leaveService.getAllLeaveTypes()));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<LeaveRequestDTO>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(leaveService.getAll()));
    }

    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<ApiResponse<List<LeaveRequestDTO>>> getByEmployee(@PathVariable Long employeeId) {
        return ResponseEntity.ok(ApiResponse.success(leaveService.getByEmployee(employeeId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<LeaveRequestDTO>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(leaveService.getById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<LeaveRequestDTO>> create(@Valid @RequestBody LeaveRequestDTO dto) {
        return ResponseEntity.ok(ApiResponse.success(leaveService.create(dto)));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<LeaveRequestDTO>> updateStatus(
            @PathVariable Long id,
            @RequestParam LeaveStatus status,
            @RequestParam(required = false) Long approvedBy) {
        return ResponseEntity.ok(ApiResponse.success(leaveService.updateStatus(id, status, approvedBy)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        leaveService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
