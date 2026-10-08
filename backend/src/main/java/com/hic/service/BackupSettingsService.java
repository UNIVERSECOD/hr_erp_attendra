package com.hic.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hic.dto.BackupSettingsResponse;
import com.hic.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
@Slf4j
public class BackupSettingsService {

    static final int RETENTION_DAYS = 183;
    static final String DEFAULT_FOLDER = "C:\\AttendraBackups\\daily";
    private static final Pattern DRIVE_PATH = Pattern.compile("^[A-Za-z]:\\\\.+");
    private static final Pattern UNC_PATH = Pattern.compile("^\\\\\\\\[^\\\\]+\\\\[^\\\\]+(?:\\\\.*)?$");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Path settingsFile;
    private final Path statusFile;
    private final Path faceImagesDirectory;

    public BackupSettingsService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            @Value("${app.runtime-dir:runtime}") String runtimeDirectory,
            @Value("${app.face-images.dir:uploads/faces}") String faceImagesDirectory) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        Path runtimePath = Path.of(runtimeDirectory).toAbsolutePath().normalize();
        this.settingsFile = runtimePath.resolve("backup-settings.json");
        this.statusFile = runtimePath.resolve("backup-status.json");
        this.faceImagesDirectory = Path.of(faceImagesDirectory).toAbsolutePath().normalize();
    }

    public BackupSettingsResponse getSettings() {
        BackupConfiguration configuration = readConfiguration();
        BackupStatus status = readStatus();
        long backendDatabaseBytes = databaseSize("SELECT pg_database_size(current_database())");
        long isapiDatabaseBytes = databaseSize("SELECT pg_database_size('hic_isapi')");
        long faceImagesBytes = directorySize(faceImagesDirectory);

        return new BackupSettingsResponse(
                configuration.enabled(),
                configuration.folderPath(),
                RETENTION_DAYS,
                status.status(),
                status.lastBackupAt(),
                status.message(),
                status.lastBackupBytes(),
                status.totalBackupBytes(),
                backendDatabaseBytes,
                isapiDatabaseBytes,
                faceImagesBytes,
                backendDatabaseBytes + isapiDatabaseBytes + faceImagesBytes);
    }

    public BackupSettingsResponse updateSettings(boolean enabled, String folderPath) {
        String validatedPath = validateHostPath(folderPath);
        writeConfiguration(new BackupConfiguration(enabled, validatedPath, RETENTION_DAYS));
        return getSettings();
    }

    private String validateHostPath(String folderPath) {
        String value = folderPath == null ? "" : folderPath.trim();
        if (value.isEmpty() || value.contains("\0") || value.contains("\r") || value.contains("\n")) {
            throw new BadRequestException("Backup qovluğunun yolu düzgün deyil");
        }
        if (value.contains("..")) {
            throw new BadRequestException("Backup qovluğunun yolunda '..' istifadə edilə bilməz");
        }
        String normalized = value.replace('/', '\\').replaceAll("\\\\+$", "");
        if (!DRIVE_PATH.matcher(normalized).matches() && !UNC_PATH.matcher(normalized).matches()) {
            throw new BadRequestException("Windows qovluğunun tam yolunu daxil edin, məsələn C:\\AttendraBackups\\daily");
        }

        String upper = normalized.toUpperCase(Locale.ROOT);
        if (upper.matches("^[A-Z]:$")
                || upper.matches("^[A-Z]:\\\\$")
                || upper.equals("C:\\WINDOWS")
                || upper.startsWith("C:\\WINDOWS\\")
                || upper.equals("C:\\PROGRAM FILES")
                || upper.startsWith("C:\\PROGRAM FILES\\")) {
            throw new BadRequestException("Sistem qovluğu backup üçün seçilə bilməz");
        }
        return normalized;
    }

    private BackupConfiguration readConfiguration() {
        if (!Files.exists(settingsFile)) {
            return new BackupConfiguration(true, DEFAULT_FOLDER, RETENTION_DAYS);
        }
        try {
            JsonNode node = objectMapper.readTree(settingsFile.toFile());
            return new BackupConfiguration(
                    node.path("enabled").asBoolean(true),
                    node.path("folderPath").asText(DEFAULT_FOLDER),
                    RETENTION_DAYS);
        } catch (IOException exception) {
            log.warn("Backup configuration could not be read: {}", exception.getMessage());
            return new BackupConfiguration(true, DEFAULT_FOLDER, RETENTION_DAYS);
        }
    }

    private void writeConfiguration(BackupConfiguration configuration) {
        try {
            Files.createDirectories(settingsFile.getParent());
            Path temporaryFile = settingsFile.resolveSibling(settingsFile.getFileName() + ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(temporaryFile.toFile(), configuration);
            try {
                Files.move(temporaryFile, settingsFile,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporaryFile, settingsFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            log.error("Backup configuration could not be saved", exception);
            throw new BadRequestException("Backup parametrlərini yadda saxlamaq mümkün olmadı");
        }
    }

    private BackupStatus readStatus() {
        if (!Files.exists(statusFile)) {
            return BackupStatus.empty();
        }
        try {
            JsonNode node = objectMapper.readTree(statusFile.toFile());
            return new BackupStatus(
                    node.path("status").asText("NEVER"),
                    nullableText(node, "lastBackupAt"),
                    nullableText(node, "message"),
                    node.path("lastBackupBytes").asLong(0),
                    node.path("totalBackupBytes").asLong(0));
        } catch (IOException exception) {
            log.warn("Backup status could not be read: {}", exception.getMessage());
            return new BackupStatus("ERROR", null, "Backup statusunu oxumaq mümkün olmadı", 0, 0);
        }
    }

    private String nullableText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }

    private long databaseSize(String sql) {
        try {
            Long size = jdbcTemplate.queryForObject(sql, Long.class);
            return size == null ? 0 : size;
        } catch (RuntimeException exception) {
            log.warn("Database size could not be calculated: {}", exception.getMessage());
            return 0;
        }
    }

    private long directorySize(Path directory) {
        if (!Files.isDirectory(directory)) {
            return 0;
        }
        try (Stream<Path> files = Files.walk(directory)) {
            return files.filter(Files::isRegularFile)
                    .mapToLong(this::fileSize)
                    .sum();
        } catch (IOException exception) {
            log.warn("Face image storage size could not be calculated: {}", exception.getMessage());
            return 0;
        }
    }

    private long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException exception) {
            return 0;
        }
    }

    private record BackupConfiguration(boolean enabled, String folderPath, int retentionDays) {
    }

    private record BackupStatus(
            String status,
            String lastBackupAt,
            String message,
            long lastBackupBytes,
            long totalBackupBytes) {

        private static BackupStatus empty() {
            return new BackupStatus("NEVER", null, null, 0, 0);
        }
    }
}
