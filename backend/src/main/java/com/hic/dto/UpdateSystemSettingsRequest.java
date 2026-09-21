package com.hic.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateSystemSettingsRequest {

    @NotNull(message = "Sync interval is required")
    private Integer syncIntervalMinutes;
}
