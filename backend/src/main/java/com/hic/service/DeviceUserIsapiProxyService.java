package com.hic.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartHttpServletRequest;

@Service
@RequiredArgsConstructor
public class DeviceUserIsapiProxyService {

    private final IsapiProxyService isapiProxyService;

    public ResponseEntity<String> createUser(Long deviceId, HttpServletRequest request, String body) {
        return isapiProxyService.forward(HttpMethod.POST, buildBasePath(deviceId), request, body);
    }

    public ResponseEntity<String> listUsers(Long deviceId, HttpServletRequest request) {
        return isapiProxyService.forward(HttpMethod.GET, buildBasePath(deviceId), request, null);
    }

    public ResponseEntity<String> getUser(Long deviceId, Long userId, HttpServletRequest request) {
        return isapiProxyService.forward(HttpMethod.GET, buildUserPath(deviceId, userId), request, null);
    }

    public ResponseEntity<String> updateUser(Long deviceId, Long userId, HttpServletRequest request, String body) {
        return isapiProxyService.forward(HttpMethod.PUT, buildUserPath(deviceId, userId), request, body);
    }

    public ResponseEntity<String> deleteUser(Long deviceId, Long userId, HttpServletRequest request) {
        return isapiProxyService.forward(HttpMethod.DELETE, buildUserPath(deviceId, userId), request, null);
    }

    public ResponseEntity<String> syncUser(Long deviceId, Long userId, HttpServletRequest request) {
        return isapiProxyService.forward(HttpMethod.POST, buildUserPath(deviceId, userId) + "/sync", request, null);
    }

    public ResponseEntity<String> uploadFace(Long deviceId, Long userId, MultipartHttpServletRequest request) {
        return isapiProxyService.forwardMultipart(buildUserPath(deviceId, userId) + "/face", request);
    }

    public ResponseEntity<String> syncFace(Long deviceId, Long userId, HttpServletRequest request) {
        return isapiProxyService.forward(HttpMethod.POST, buildUserPath(deviceId, userId) + "/face/sync", request, null);
    }

    public ResponseEntity<String> deleteFace(Long deviceId, Long userId, HttpServletRequest request) {
        return isapiProxyService.forward(HttpMethod.DELETE, buildUserPath(deviceId, userId) + "/face", request, null);
    }

    private String buildBasePath(Long deviceId) {
        return "/api/devices/" + deviceId + "/users";
    }

    private String buildUserPath(Long deviceId, Long userId) {
        return buildBasePath(deviceId) + "/" + userId;
    }
}
