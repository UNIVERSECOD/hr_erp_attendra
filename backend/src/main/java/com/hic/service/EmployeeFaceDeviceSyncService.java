package com.hic.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hic.model.Employee;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EmployeeFaceDeviceSyncService {

    private static final int USER_LOOKUP_ATTEMPTS = 3;
    private static final long USER_LOOKUP_DELAY_MS = 250L;

    public enum SyncOutcome {
        SYNCED,
        NO_FACE,
        ALREADY_PRESENT,
        NOT_CONFIGURED
    }

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final EmployeeFaceImageService employeeFaceImageService;

    @Value("${isapi.base-url:}")
    private String isapiBaseUrl;

    public SyncOutcome syncIfAvailable(Employee employee, Long isapiDeviceId) {
        Optional<EmployeeFaceImageService.FaceImageData> face =
                employeeFaceImageService.getLatestFaceImage(employee.getId());
        if (face.isEmpty()) {
            return SyncOutcome.NO_FACE;
        }
        if (!StringUtils.hasText(isapiBaseUrl)) {
            return SyncOutcome.NOT_CONFIGURED;
        }

        try {
            Long deviceUserId = findDeviceUserIdWithRetry(isapiDeviceId, resolveDevicePersonNo(employee));
            if (deviceUserId == null) {
                throw new IllegalStateException("Device user was not found after identity synchronization");
            }
            byte[] imageBytes = Files.readAllBytes(face.get().path());
            uploadFace(isapiDeviceId, deviceUserId, imageBytes, face.get().contentType());
            return SyncOutcome.SYNCED;
        } catch (HttpStatusCodeException ex) {
            if (isAlreadyPresent(ex)) {
                return SyncOutcome.ALREADY_PRESENT;
            }
            throw ex;
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Face synchronization failed: " + ex.getMessage(), ex);
        }
    }

    private Long findDeviceUserIdWithRetry(Long isapiDeviceId, String employeeNo) throws Exception {
        for (int attempt = 1; attempt <= USER_LOOKUP_ATTEMPTS; attempt++) {
            Long userId = findDeviceUserId(isapiDeviceId, employeeNo);
            if (userId != null) {
                return userId;
            }
            if (attempt < USER_LOOKUP_ATTEMPTS) {
                try {
                    Thread.sleep(USER_LOOKUP_DELAY_MS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
        return null;
    }

    private Long findDeviceUserId(Long isapiDeviceId, String employeeNo) throws Exception {
        String url = trimTrailingSlash(isapiBaseUrl) + "/api/devices/" + isapiDeviceId + "/users";
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, null, String.class);
        if (response.getBody() == null || response.getBody().isBlank()) {
            return null;
        }

        String body = response.getBody().trim();
        List<DeviceUser> users;
        if (body.startsWith("[")) {
            users = objectMapper.readValue(body, new TypeReference<>() { });
        } else {
            JsonNode root = objectMapper.readTree(body);
            JsonNode data = root.get("data");
            users = objectMapper.convertValue(data != null && data.isArray() ? data : root, new TypeReference<>() { });
        }

        String normalizedEmployeeNo = employeeNo.trim().toLowerCase(Locale.ROOT);
        return users.stream()
                .filter(user -> user != null && user.getId() != null && StringUtils.hasText(user.getEmployeeNo()))
                .filter(user -> normalizedEmployeeNo.equals(user.getEmployeeNo().trim().toLowerCase(Locale.ROOT)))
                .map(DeviceUser::getId)
                .findFirst()
                .orElse(null);
    }

    private void uploadFace(
            Long isapiDeviceId,
            Long deviceUserId,
            byte[] imageBytes,
            String contentType) {
        String url = trimTrailingSlash(isapiBaseUrl)
                + "/api/devices/" + isapiDeviceId + "/users/" + deviceUserId + "/face";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ByteArrayResource resource = new ByteArrayResource(imageBytes) {
            @Override
            public String getFilename() {
                if (contentType != null && contentType.contains("png")) {
                    return "face.png";
                }
                if (contentType != null && contentType.contains("webp")) {
                    return "face.webp";
                }
                return "face.jpg";
            }
        };
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", resource);
        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("Face upload HTTP " + response.getStatusCode().value());
        }
    }

    private boolean isAlreadyPresent(HttpStatusCodeException ex) {
        String message = ex.getResponseBodyAsString();
        if (!StringUtils.hasText(message)) {
            message = ex.getMessage();
        }
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
        return ex.getStatusCode().value() == 409
                || normalized.contains("already exist")
                || normalized.contains("duplicate");
    }

    private String resolveDevicePersonNo(Employee employee) {
        if (StringUtils.hasText(employee.getDeviceEmployeeNo())) {
            return employee.getDeviceEmployeeNo().trim();
        }
        return employee.getEmployeeId();
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    @Data
    private static class DeviceUser {
        private Long id;
        private String employeeNo;
    }
}
