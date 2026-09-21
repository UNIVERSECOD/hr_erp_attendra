package com.hic.controller;

import com.hic.dto.ApiResponse;
import com.hic.dto.DoorDTO;
import com.hic.service.DoorService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/doors")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('HEAD_OFFICE_HR','OFFICE_HR','DEPARTMENT_HR')")
public class DoorController {

    private final DoorService doorService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<DoorDTO.Response>>> getDoors(@RequestParam Long branchId) {
        return ResponseEntity.ok(ApiResponse.success(doorService.findByBranch(branchId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DoorDTO.Response>> createDoor(@RequestBody DoorDTO.CreateRequest dto) {
        return ResponseEntity.ok(ApiResponse.success(doorService.create(dto)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<DoorDTO.Response>> updateDoor(
            @PathVariable Long id,
            @RequestBody DoorDTO.UpdateRequest dto) {
        return ResponseEntity.ok(ApiResponse.success(doorService.update(id, dto)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteDoor(@PathVariable Long id) {
        doorService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
