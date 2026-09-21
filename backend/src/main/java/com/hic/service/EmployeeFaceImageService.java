package com.hic.service;

import com.hic.model.FaceData;
import com.hic.repository.FaceDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmployeeFaceImageService {

    private final FaceDataRepository faceDataRepository;

    @Value("${app.face-images.dir:uploads/faces}")
    private String faceImagesDir;

    public void saveFaceImage(Long employeeId, MultipartFile file) {
        if (employeeId == null || file == null || file.isEmpty()) {
            return;
        }

        String extension = resolveExtension(file);
        String fileName = "emp-" + employeeId + "-" + UUID.randomUUID() + "." + extension;
        Path targetDir = Paths.get(faceImagesDir).toAbsolutePath().normalize();
        Path targetFile = targetDir.resolve(fileName).normalize();

        if (!targetFile.startsWith(targetDir)) {
            throw new IllegalStateException("Invalid target path for employee face image");
        }

        try {
            Files.createDirectories(targetDir);
            Files.copy(file.getInputStream(), targetFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store employee face image", e);
        }

        FaceData faceData = new FaceData();
        faceData.setEmployeeId(employeeId);
        faceData.setFaceId(UUID.randomUUID().toString());
        faceData.setFaceImageUrl(fileName);
        faceData.setStatus("ACTIVE");
        faceDataRepository.save(faceData);
    }

    public void saveFaceImageBytes(Long employeeId, byte[] bytes, String extensionHint) {
        if (employeeId == null || bytes == null || bytes.length == 0) {
            return;
        }

        String extension = resolveExtension(extensionHint);
        String fileName = "emp-" + employeeId + "-" + UUID.randomUUID() + "." + extension;
        Path targetDir = Paths.get(faceImagesDir).toAbsolutePath().normalize();
        Path targetFile = targetDir.resolve(fileName).normalize();

        if (!targetFile.startsWith(targetDir)) {
            throw new IllegalStateException("Invalid target path for employee face image");
        }

        try {
            Files.createDirectories(targetDir);
            Files.write(targetFile, bytes);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store employee face image", e);
        }

        FaceData faceData = new FaceData();
        faceData.setEmployeeId(employeeId);
        faceData.setFaceId(UUID.randomUUID().toString());
        faceData.setFaceImageUrl(fileName);
        faceData.setStatus("ACTIVE");
        faceDataRepository.save(faceData);
    }

    public Optional<String> getLatestEmployeeFacePublicUrl(Long employeeId) {
        return getLatestFaceImage(employeeId)
                .map(faceImage -> "/api/faces/employee/" + employeeId + "/image");
    }

    public boolean hasFaceImage(Long employeeId) {
        if (employeeId == null) {
            return false;
        }
        return getLatestFaceImage(employeeId).isPresent();
    }

    public Optional<FaceImageData> getLatestFaceImage(Long employeeId) {
        return faceDataRepository.findTopByEmployeeIdOrderByCreatedAtDesc(employeeId)
                .flatMap(this::toFaceImageData);
    }

    public void deleteFaceImages(Long employeeId) {
        if (employeeId == null) {
            return;
        }

        List<FaceData> faceDataList = faceDataRepository.findByEmployeeId(employeeId);
        if (faceDataList.isEmpty()) {
            return;
        }

        Path targetDir = Paths.get(faceImagesDir).toAbsolutePath().normalize();
        for (FaceData faceData : faceDataList) {
            if (!StringUtils.hasText(faceData.getFaceImageUrl())) {
                continue;
            }
            Path filePath = targetDir.resolve(faceData.getFaceImageUrl()).normalize();
            if (filePath.startsWith(targetDir)) {
                try {
                    Files.deleteIfExists(filePath);
                } catch (IOException ignored) {
                }
            }
        }
        faceDataRepository.deleteAll(faceDataList);
    }

    private Optional<FaceImageData> toFaceImageData(FaceData faceData) {
        if (!StringUtils.hasText(faceData.getFaceImageUrl())) {
            return Optional.empty();
        }

        Path targetDir = Paths.get(faceImagesDir).toAbsolutePath().normalize();
        Path filePath = targetDir.resolve(faceData.getFaceImageUrl()).normalize();

        if (!filePath.startsWith(targetDir) || !Files.exists(filePath)) {
            return Optional.empty();
        }

        String fileName = filePath.getFileName().toString().toLowerCase();
        String contentType;
        if (fileName.endsWith(".png")) {
            contentType = "image/png";
        } else if (fileName.endsWith(".webp")) {
            contentType = "image/webp";
        } else {
            contentType = "image/jpeg";
        }

        return Optional.of(new FaceImageData(filePath, contentType));
    }

    private String resolveExtension(MultipartFile file) {
        String contentType = file.getContentType();
        if ("image/png".equalsIgnoreCase(contentType)) {
            return "png";
        }
        if ("image/webp".equalsIgnoreCase(contentType)) {
            return "webp";
        }
        String original = file.getOriginalFilename();
        if (StringUtils.hasText(original) && original.contains(".")) {
            String ext = original.substring(original.lastIndexOf('.') + 1).toLowerCase();
            if (ext.matches("[a-z0-9]{2,5}")) {
                return ext;
            }
        }
        return "jpg";
    }

    private String resolveExtension(String extensionHint) {
        if (!StringUtils.hasText(extensionHint)) {
            return "jpg";
        }
        String ext = extensionHint.toLowerCase();
        if (ext.startsWith(".")) {
            ext = ext.substring(1);
        }
        if (ext.matches("[a-z0-9]{2,5}")) {
            return ext;
        }
        return "jpg";
    }

    public record FaceImageData(Path path, String contentType) {
    }
}
