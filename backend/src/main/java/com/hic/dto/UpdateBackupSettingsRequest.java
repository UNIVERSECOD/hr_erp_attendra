package com.hic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateBackupSettingsRequest(
        boolean enabled,
        @NotBlank(message = "Backup qovluğu daxil edilməlidir")
        @Size(max = 1024, message = "Backup qovluğunun yolu çox uzundur")
        String folderPath) {
}
