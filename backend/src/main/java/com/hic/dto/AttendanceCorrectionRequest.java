package com.hic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AttendanceCorrectionRequest {

    @NotNull
    private LocalDateTime checkInTime;

    @NotNull
    private LocalDateTime checkOutTime;

    @NotBlank
    @Size(max = 500)
    private String reason;
}
