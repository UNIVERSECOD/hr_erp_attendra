package com.hic.controller;

import com.hic.dto.ApiResponse;
import com.hic.dto.HolidayDTO;
import com.hic.service.HolidayService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/holidays")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('HEAD_OFFICE_HR','OFFICE_HR','DEPARTMENT_HR')")
public class HolidayController {
    private final HolidayService holidayService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<HolidayDTO>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(holidayService.getAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<HolidayDTO>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(holidayService.getById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<HolidayDTO>> create(@RequestBody HolidayDTO dto) {
        return ResponseEntity.ok(ApiResponse.success(holidayService.create(dto)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<HolidayDTO>> update(@PathVariable Long id, @RequestBody HolidayDTO dto) {
        return ResponseEntity.ok(ApiResponse.success(holidayService.update(id, dto)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        holidayService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
