package com.hic.controller;

import com.hic.service.DeviceUserIsapiProxyService;
import com.hic.service.EmployeeFaceImageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.util.Base64;

@RestController
@RequestMapping(value = "/api/devices/{deviceId}/users", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('HEAD_OFFICE_HR','OFFICE_HR','DEPARTMENT_HR')")
public class DeviceUserController {

    private static final ObjectMapper OM = new ObjectMapper();

    private final DeviceUserIsapiProxyService deviceUserIsapiProxyService;
    private final EmployeeFaceImageService employeeFaceImageService;

    @PostMapping
    public ResponseEntity<String> createUser(@PathVariable Long deviceId,
                                             @RequestBody(required = false) String body,
                                             HttpServletRequest request) {
        return deviceUserIsapiProxyService.createUser(deviceId, request, body);
    }

    @GetMapping
    public ResponseEntity<String> getUsers(@PathVariable Long deviceId, HttpServletRequest request) {
        return deviceUserIsapiProxyService.listUsers(deviceId, request);
    }

    @GetMapping("/{userId}")
    public ResponseEntity<String> getUser(@PathVariable Long deviceId,
                                          @PathVariable Long userId,
                                          HttpServletRequest request) {
        return deviceUserIsapiProxyService.getUser(deviceId, userId, request);
    }

    @PutMapping("/{userId}")
    public ResponseEntity<String> updateUser(@PathVariable Long deviceId,
                                             @PathVariable Long userId,
                                             @RequestBody(required = false) String body,
                                             HttpServletRequest request) {
        return deviceUserIsapiProxyService.updateUser(deviceId, userId, request, body);
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<String> deleteUser(@PathVariable Long deviceId,
                                             @PathVariable Long userId,
                                             HttpServletRequest request) {
        return deviceUserIsapiProxyService.deleteUser(deviceId, userId, request);
    }

    @PostMapping("/{userId}/sync")
    public ResponseEntity<String> syncUser(@PathVariable Long deviceId,
                                            @PathVariable Long userId,
                                            HttpServletRequest request) {
        return deviceUserIsapiProxyService.syncUser(deviceId, userId, request);
    }

    @PostMapping(path = "/{userId}/face", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> uploadFace(@PathVariable Long deviceId,
                                             @PathVariable Long userId,
                                             @RequestParam(required = false) Long employeeId,
                                             MultipartHttpServletRequest request) {
        MultipartFile file = request.getFile("file");
        ResponseEntity<String> response = deviceUserIsapiProxyService.uploadFace(deviceId, userId, request);
        if (response.getStatusCode().is2xxSuccessful() && employeeId != null) {
            employeeFaceImageService.saveFaceImage(employeeId, file);
        }
        return response;
    }

    @PostMapping(path = "/{userId}/face/sync")
    public ResponseEntity<String> syncFace(@PathVariable Long deviceId,
                                           @PathVariable Long userId,
                                           @RequestParam(required = false) Long employeeId,
                                           HttpServletRequest request) {
        ResponseEntity<String> response = deviceUserIsapiProxyService.syncFace(deviceId, userId, request);
        if (!response.getStatusCode().is2xxSuccessful() || employeeId == null || response.getBody() == null) {
            return response;
        }

        try {
            JsonNode root = OM.readTree(response.getBody());
            String status = root.path("status").asText("");
            String imageBase64 = root.path("imageBase64").asText("");
            if ("SUCCESS".equalsIgnoreCase(status) && !imageBase64.isBlank()) {
                byte[] imageBytes = Base64.getDecoder().decode(imageBase64);
                employeeFaceImageService.saveFaceImageBytes(employeeId, imageBytes, "jpg");
            }
        } catch (Exception ignored) {
        }

        return response;
    }

    @DeleteMapping("/{userId}/face")
    public ResponseEntity<String> deleteFace(@PathVariable Long deviceId,
                                             @PathVariable Long userId,
                                             @RequestParam(required = false) Long employeeId,
                                             HttpServletRequest request) {
        ResponseEntity<String> response = deviceUserIsapiProxyService.deleteFace(deviceId, userId, request);
        if (employeeId != null && (response.getStatusCode().is2xxSuccessful() || response.getStatusCode().value() == 404)) {
            employeeFaceImageService.deleteFaceImages(employeeId);
        }
        return response;
    }
}
