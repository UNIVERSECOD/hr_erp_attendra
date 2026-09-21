package com.hic.controller;

import com.hic.service.EmployeeFaceImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/faces")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('HEAD_OFFICE_HR','OFFICE_HR','DEPARTMENT_HR')")
public class EmployeeFaceController {

    private final EmployeeFaceImageService employeeFaceImageService;

    @GetMapping("/employee/{employeeId}/image")
    public ResponseEntity<FileSystemResource> getEmployeeFaceImage(@PathVariable Long employeeId) {
        return employeeFaceImageService.getLatestFaceImage(employeeId)
                .map(faceImage -> ResponseEntity.ok()
                        .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                        .contentType(MediaType.parseMediaType(faceImage.contentType()))
                        .body(new FileSystemResource(faceImage.path())))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    /**
     * Persist the captured/uploaded employee photo as the profile face image,
     * independent of Hikvision device sync success.
     */
    @PostMapping(path = "/employee/{employeeId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> uploadEmployeeFaceImage(@PathVariable Long employeeId,
                                                        @RequestParam("file") MultipartFile file) {
        employeeFaceImageService.saveFaceImage(employeeId, file);
        return ResponseEntity.ok().build();
    }
}
