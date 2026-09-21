package com.hic.controller;

import com.hic.dto.BranchDTO;
import com.hic.dto.ApiResponse;
import com.hic.service.BranchService;
import com.hic.util.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/branches")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('HEAD_OFFICE_HR','OFFICE_HR','DEPARTMENT_HR')")
public class BranchController {
    private final BranchService branchService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<BranchDTO>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(branchService.getAll(TenantContext.getTenantId())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BranchDTO>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(branchService.getById(TenantContext.getTenantId(), id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<BranchDTO>> create(@Valid @RequestBody BranchDTO dto) {
        return ResponseEntity.ok(ApiResponse.success(branchService.create(TenantContext.getTenantId(), dto)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<BranchDTO>> update(@PathVariable Long id, @Valid @RequestBody BranchDTO dto) {
        return ResponseEntity.ok(ApiResponse.success(branchService.update(TenantContext.getTenantId(), id, dto)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        branchService.delete(TenantContext.getTenantId(), id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
