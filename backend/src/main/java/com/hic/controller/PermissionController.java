package com.hic.controller;

import com.hic.dto.ApiResponse;
import com.hic.dto.PermissionDTO;
import com.hic.dto.PermissionTypeDTO;
import com.hic.dto.PaginatedResponse;
import com.hic.service.PermissionService;
import com.hic.service.PermissionTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/permissions")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('HEAD_OFFICE_HR','OFFICE_HR','DEPARTMENT_HR')")
public class PermissionController {
    private final PermissionService permissionService;
    private final PermissionTypeService permissionTypeService;

    @GetMapping
    public ResponseEntity<ApiResponse<PaginatedResponse<PermissionDTO>>> getAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(permissionService.getAll(search, status, type, start, end, page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PermissionDTO>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(permissionService.getById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PermissionDTO>> create(@RequestBody PermissionDTO dto) {
        return ResponseEntity.ok(ApiResponse.success(permissionService.create(dto)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PermissionDTO>> update(@PathVariable Long id, @RequestBody PermissionDTO dto) {
        return ResponseEntity.ok(ApiResponse.success(permissionService.update(id, dto)));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<PermissionDTO>> approve(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(permissionService.approve(id)));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<PermissionDTO>> reject(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(permissionService.reject(id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        permissionService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/employee/{employeePk}")
    public ResponseEntity<ApiResponse<List<PermissionDTO>>> getEmployeeHistory(
            @PathVariable Long employeePk,
            @RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(ApiResponse.success(permissionService.getEmployeeHistory(employeePk, year)));
    }

    @GetMapping("/types")
    public ResponseEntity<ApiResponse<List<PermissionTypeDTO>>> getTypes() {
        return ResponseEntity.ok(ApiResponse.success(permissionTypeService.getAll()));
    }

    @PostMapping("/types")
    public ResponseEntity<ApiResponse<PermissionTypeDTO>> createType(@RequestBody PermissionTypeDTO dto) {
        return ResponseEntity.ok(ApiResponse.success(permissionTypeService.create(dto)));
    }

    @DeleteMapping("/types/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteType(@PathVariable Long id) {
        permissionTypeService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
