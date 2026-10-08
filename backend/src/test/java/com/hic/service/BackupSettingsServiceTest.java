package com.hic.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hic.dto.BackupSettingsResponse;
import com.hic.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BackupSettingsServiceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void updateSettings_persistsSafeWindowsPathAndReturnsStorageSizes() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, String.class).contains("current_database")
                        ? 1_000L
                        : 2_000L);
        Path faces = temporaryDirectory.resolve("faces");
        Files.createDirectories(faces);
        Files.write(faces.resolve("employee.jpg"), new byte[300]);
        BackupSettingsService service = new BackupSettingsService(
                jdbcTemplate,
                new ObjectMapper(),
                temporaryDirectory.resolve("runtime").toString(),
                faces.toString());

        BackupSettingsResponse response = service.updateSettings(true, "D:\\AttendraBackups\\daily\\");

        assertThat(response.enabled()).isTrue();
        assertThat(response.folderPath()).isEqualTo("D:\\AttendraBackups\\daily");
        assertThat(response.retentionDays()).isEqualTo(183);
        assertThat(response.backendDatabaseBytes()).isEqualTo(1_000L);
        assertThat(response.isapiDatabaseBytes()).isEqualTo(2_000L);
        assertThat(response.faceImagesBytes()).isEqualTo(300L);
        assertThat(response.currentDataBytes()).isEqualTo(3_300L);
        assertThat(Files.readString(temporaryDirectory.resolve("runtime/backup-settings.json")))
                .contains("D:\\\\AttendraBackups\\\\daily");
    }

    @Test
    void getSettings_readsLastBackupStatus() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(100L);
        Path runtime = temporaryDirectory.resolve("runtime");
        Files.createDirectories(runtime);
        Files.writeString(runtime.resolve("backup-status.json"), """
                {
                  "status": "SUCCESS",
                  "lastBackupAt": "2026-10-08T04:00:00+04:00",
                  "message": "Backup uğurla tamamlandı.",
                  "lastBackupBytes": 500,
                  "totalBackupBytes": 1500
                }
                """);
        BackupSettingsService service = new BackupSettingsService(
                jdbcTemplate, new ObjectMapper(), runtime.toString(), temporaryDirectory.resolve("faces").toString());

        BackupSettingsResponse response = service.getSettings();

        assertThat(response.lastStatus()).isEqualTo("SUCCESS");
        assertThat(response.lastBackupBytes()).isEqualTo(500L);
        assertThat(response.totalBackupBytes()).isEqualTo(1_500L);
    }

    @Test
    void updateSettings_rejectsRelativeAndSystemPaths() {
        BackupSettingsService service = new BackupSettingsService(
                mock(JdbcTemplate.class),
                new ObjectMapper(),
                temporaryDirectory.resolve("runtime").toString(),
                temporaryDirectory.resolve("faces").toString());

        assertThatThrownBy(() -> service.updateSettings(true, "backups"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("tam yolunu");
        assertThatThrownBy(() -> service.updateSettings(true, "C:\\Windows\\Temp"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Sistem qovluğu");
        assertThatThrownBy(() -> service.updateSettings(true, "D:\\Backups\\..\\Other"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("'..'");
    }

    @Test
    void updateSettings_normalizesForwardSlashes() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
        BackupSettingsService service = new BackupSettingsService(
                jdbcTemplate,
                new ObjectMapper(),
                temporaryDirectory.resolve("runtime").toString(),
                temporaryDirectory.resolve("faces").toString());

        BackupSettingsResponse response = service.updateSettings(true, "D:/AttendraBackups/daily");

        assertThat(response.folderPath()).isEqualTo("D:\\AttendraBackups\\daily");
    }
}
