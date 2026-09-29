package com.hic.controller;

import com.hic.dto.ApiResponse;
import com.hic.dto.DataImportResultDTO;
import com.hic.service.MasterDataTransferService;
import com.hic.service.MasterDataTransferService.DataFile;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/data-transfer")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('HEAD_OFFICE_HR','OFFICE_HR','DEPARTMENT_HR')")
public class MasterDataTransferController {

    private final MasterDataTransferService transferService;

    @GetMapping("/{entity}/export")
    public ResponseEntity<byte[]> export(
            @PathVariable String entity,
            @RequestParam(defaultValue = "xlsx") String format) {
        return download(transferService.exportData(entity, format, false));
    }

    @GetMapping("/{entity}/template")
    public ResponseEntity<byte[]> template(
            @PathVariable String entity,
            @RequestParam(defaultValue = "xlsx") String format) {
        return download(transferService.exportData(entity, format, true));
    }

    @PostMapping(value = "/{entity}/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<DataImportResultDTO>> importFile(
            @PathVariable String entity,
            @RequestParam("file") MultipartFile file) {
        DataImportResultDTO result = transferService.importData(entity, file);
        String message = result.isSuccessful()
                ? result.getImportedRows() + " sətir uğurla import edildi"
                : "Faylda xətalar var. Heç bir məlumat import edilmədi";
        return ResponseEntity.ok(new ApiResponse<>(result.isSuccessful(), message, result));
    }

    private ResponseEntity<byte[]> download(DataFile file) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.filename() + "\"")
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.content().length)
                .body(file.content());
    }
}
