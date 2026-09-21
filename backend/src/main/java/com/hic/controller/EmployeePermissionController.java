package com.hic.controller;

import com.hic.dto.ApiResponse;
import com.hic.dto.EmployeePermissionBulkRequest;
import com.hic.dto.EmployeePermissionDTO;
import com.hic.model.EmployeePermission;
import com.hic.service.EmployeePermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/employee-permissions")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('HEAD_OFFICE_HR','OFFICE_HR','DEPARTMENT_HR')")
public class EmployeePermissionController {

    private final EmployeePermissionService employeePermissionService;

    @PostMapping
    public ResponseEntity<ApiResponse<EmployeePermissionDTO>> grant(@RequestBody EmployeePermissionDTO dto) {
        return ResponseEntity.ok(ApiResponse.success(employeePermissionService.grantPermission(
                dto.getEmployeeId(),
                dto.getPermissionTypeId(),
                dto.getStartDate(),
                dto.getEndDate(),
                dto.getReason(),
                dto.getStatus()
        )));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<EmployeePermissionDTO>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(employeePermissionService.getAll()));
    }

    @GetMapping("/permission-type/{id}")
    public ResponseEntity<ApiResponse<List<EmployeePermissionDTO>>> getByPermissionType(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.success(employeePermissionService.getEmployeesWithPermission(id, date)));
    }

    @GetMapping("/employee/{id}")
    public ResponseEntity<ApiResponse<List<EmployeePermissionDTO>>> getEmployeePermissions(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(employeePermissionService.getPermissionHistory(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<EmployeePermissionDTO>> update(@PathVariable Long id, @RequestBody EmployeePermissionDTO dto) {
        return ResponseEntity.ok(ApiResponse.success(employeePermissionService.updatePermission(
                id,
                dto.getStartDate(),
                dto.getEndDate(),
                dto.getReason(),
                dto.getStatus()
        )));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> revoke(@PathVariable Long id) {
        employeePermissionService.revokePermission(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/bulk")
    public ResponseEntity<ApiResponse<List<EmployeePermissionDTO>>> bulkGrant(@RequestBody EmployeePermissionBulkRequest request) {
        EmployeePermission.Status status = request.getStatus() != null
                ? EmployeePermission.Status.valueOf(request.getStatus())
                : null;
        return ResponseEntity.ok(ApiResponse.success(employeePermissionService.bulkGrantPermission(
                request.getEmployeeIds(),
                request.getPermissionTypeId(),
                request.getStartDate(),
                request.getEndDate(),
                request.getReason(),
                status
        )));
    }
}
