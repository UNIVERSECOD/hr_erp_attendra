package com.hic.dto;

public record BackupSettingsResponse(
        boolean enabled,
        String folderPath,
        int retentionDays,
        String lastStatus,
        String lastBackupAt,
        String lastMessage,
        long lastBackupBytes,
        long totalBackupBytes,
        long backendDatabaseBytes,
        long isapiDatabaseBytes,
        long faceImagesBytes,
        long currentDataBytes) {
}
