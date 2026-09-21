package com.hic.controller;

import com.hic.dto.ApiResponse;
import com.hic.dto.PositionDTO;
import com.hic.service.PositionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/positions")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('HEAD_OFFICE_HR','OFFICE_HR','DEPARTMENT_HR')")
public class PositionController {

    private final PositionService positionService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<PositionDTO>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(positionService.getAll()));
    }

    @GetMapping("/department/{departmentId}")
    public ResponseEntity<ApiResponse<List<PositionDTO>>> getByDepartment(@PathVariable Long departmentId) {
        return ResponseEntity.ok(ApiResponse.success(positionService.getByDepartment(departmentId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PositionDTO>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(positionService.getById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PositionDTO>> create(@Valid @RequestBody PositionDTO dto) {
        return ResponseEntity.ok(ApiResponse.success(positionService.create(dto)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PositionDTO>> update(@PathVariable Long id, @Valid @RequestBody PositionDTO dto) {
        return ResponseEntity.ok(ApiResponse.success(positionService.update(id, dto)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        positionService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
