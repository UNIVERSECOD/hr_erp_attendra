package com.hic.controller;

import com.hic.dto.ApiResponse;
import com.hic.dto.EmployeeShiftAssignmentDTO;
import com.hic.dto.ShiftAssignmentBulkRequest;
import com.hic.service.ShiftAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/shift-assignments")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('HEAD_OFFICE_HR','OFFICE_HR','DEPARTMENT_HR')")
public class ShiftAssignmentController {

    private final ShiftAssignmentService shiftAssignmentService;

    @PostMapping
    public ResponseEntity<ApiResponse<EmployeeShiftAssignmentDTO>> create(@RequestBody EmployeeShiftAssignmentDTO dto) {
        return ResponseEntity.ok(ApiResponse.success(shiftAssignmentService.assignEmployeeToShift(
                dto.getEmployeeId(),
                dto.getTimetableId(),
                dto.getEffectiveStartDate(),
                dto.getEffectiveEndDate()
        )));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<EmployeeShiftAssignmentDTO>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(shiftAssignmentService.getAll()));
    }

    @GetMapping("/timetable/{id}")
    public ResponseEntity<ApiResponse<List<EmployeeShiftAssignmentDTO>>> getEmployeesForShift(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.success(shiftAssignmentService.getEmployeesForShift(id, date)));
    }

    @GetMapping("/employee/{id}")
    public ResponseEntity<ApiResponse<List<EmployeeShiftAssignmentDTO>>> getEmployeeShiftHistory(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(shiftAssignmentService.getShiftHistory(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<EmployeeShiftAssignmentDTO>> update(
            @PathVariable Long id,
            @RequestBody EmployeeShiftAssignmentDTO dto) {
        return ResponseEntity.ok(ApiResponse.success(
                shiftAssignmentService.updateAssignment(id, dto.getEffectiveStartDate(), dto.getEffectiveEndDate())
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> remove(@PathVariable Long id) {
        shiftAssignmentService.removeEmployeeFromShift(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/bulk")
    public ResponseEntity<ApiResponse<List<EmployeeShiftAssignmentDTO>>> bulkAssign(@RequestBody ShiftAssignmentBulkRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                shiftAssignmentService.bulkAssignToShift(
                        request.getEmployeeIds(),
                        request.getTimetableId(),
                        request.getStartDate(),
                        request.getEndDate()
                )
        ));
    }
}
