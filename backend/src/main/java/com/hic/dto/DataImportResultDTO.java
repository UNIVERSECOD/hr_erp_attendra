package com.hic.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataImportResultDTO {
    private String entity;
    private int totalRows;
    private int importedRows;
    private int rejectedRows;
    private boolean deviceSyncDeferred;

    @Builder.Default
    private List<DataImportErrorDTO> errors = new ArrayList<>();

    public boolean isSuccessful() {
        return errors == null || errors.isEmpty();
    }
}
