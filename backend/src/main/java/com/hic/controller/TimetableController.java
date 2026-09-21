package com.hic.controller;

import com.hic.dto.ApiResponse;
import com.hic.model.Timetable;
import com.hic.service.TimetableService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/timetables")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('HEAD_OFFICE_HR','OFFICE_HR','DEPARTMENT_HR')")
public class TimetableController {
    private final TimetableService timetableService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Timetable>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(timetableService.getAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Timetable>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(timetableService.getById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Timetable>> create(@RequestBody Timetable timetable) {
        return ResponseEntity.ok(ApiResponse.success(timetableService.create(timetable)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Timetable>> update(@PathVariable Long id, @RequestBody Timetable timetable) {
        return ResponseEntity.ok(ApiResponse.success(timetableService.update(id, timetable)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        timetableService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
