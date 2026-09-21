package com.hic.controller;

import com.hic.dto.HolidayPermissionDTO;
import com.hic.dto.ApiResponse;
import com.hic.service.HolidayPermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/holiday-permissions")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('HEAD_OFFICE_HR','OFFICE_HR','DEPARTMENT_HR')")
public class HolidayPermissionController {

    private final HolidayPermissionService holidayPermissionService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<HolidayPermissionDTO>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success("Holiday permissions listed", holidayPermissionService.getAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<HolidayPermissionDTO>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Holiday permission fetched", holidayPermissionService.getById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<HolidayPermissionDTO>> create(@RequestBody HolidayPermissionDTO dto) {
        return ResponseEntity.ok(ApiResponse.success("Holiday permission created", holidayPermissionService.create(dto)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<HolidayPermissionDTO>> update(@PathVariable Long id,
                                                                     @RequestBody HolidayPermissionDTO dto) {
        return ResponseEntity.ok(ApiResponse.success("Holiday permission updated", holidayPermissionService.update(id, dto)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        holidayPermissionService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Holiday permission deleted", null));
    }

    @GetMapping("/range")
    public ResponseEntity<ApiResponse<List<HolidayPermissionDTO>>> getRange(
            @RequestParam("start") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam("end") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {
        return ResponseEntity.ok(ApiResponse.success("Holiday permissions listed", holidayPermissionService.getRange(start, end)));
    }
}
