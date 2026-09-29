package com.hic.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataImportErrorDTO {
    private int row;
    private String field;
    private String message;
}
