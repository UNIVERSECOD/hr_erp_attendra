package com.hic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PositionDTO {

    private Long id;

    @NotBlank(message = "Position name is required")
    private String positionName;

    private String description;

    @NotNull(message = "Department ID is required")
    private Long departmentId;

    private String departmentName;

    private long employeeCount;

    private LocalDateTime createdAt;
}
