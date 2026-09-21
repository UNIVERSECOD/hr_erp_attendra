package com.hic.controller;

import com.hic.dto.ApiResponse;
import com.hic.model.WorkSchedule;
import com.hic.service.WorkScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/work-schedules")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('HEAD_OFFICE_HR','OFFICE_HR','DEPARTMENT_HR')")
public class WorkScheduleController {
    private final WorkScheduleService workScheduleService;

    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<ApiResponse<List<WorkSchedule>>> getByEmployee(@PathVariable Long employeeId) {
        return ResponseEntity.ok(ApiResponse.success(workScheduleService.getByEmployee(employeeId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<WorkSchedule>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(workScheduleService.getById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<WorkSchedule>> create(@RequestBody WorkSchedule schedule) {
        return ResponseEntity.ok(ApiResponse.success(workScheduleService.create(schedule)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<WorkSchedule>> update(@PathVariable Long id, @RequestBody WorkSchedule schedule) {
        return ResponseEntity.ok(ApiResponse.success(workScheduleService.update(id, schedule)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        workScheduleService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
