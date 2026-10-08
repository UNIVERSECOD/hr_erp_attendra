package com.hic.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hic.model.Employee;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmployeeFaceDeviceSyncServiceTest {

    @TempDir
    Path tempDir;

    private RestTemplate restTemplate;
    private EmployeeFaceImageService employeeFaceImageService;
    private EmployeeFaceDeviceSyncService service;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        employeeFaceImageService = mock(EmployeeFaceImageService.class);
        service = new EmployeeFaceDeviceSyncService(
                restTemplate,
                new ObjectMapper(),
                employeeFaceImageService);
        ReflectionTestUtils.setField(service, "isapiBaseUrl", "http://isapi:8081");
    }

    @Test
    void syncIfAvailable_uploadsFaceForExactDeviceEmployeeNumber() throws Exception {
        Employee employee = employee();
        Path facePath = tempDir.resolve("face.jpg");
        Files.write(facePath, new byte[]{1, 2, 3});

        when(employeeFaceImageService.getLatestFaceImage(7L))
                .thenReturn(Optional.of(new EmployeeFaceImageService.FaceImageData(
                        facePath,
                        "image/jpeg")));
        when(restTemplate.exchange(
                eq("http://isapi:8081/api/devices/101/users"),
                eq(HttpMethod.GET),
                isNull(),
                eq(String.class)))
                .thenReturn(ResponseEntity.ok("[{\"id\":77,\"employeeNo\":\"1234\"}]"));
        when(restTemplate.exchange(
                eq("http://isapi:8081/api/devices/101/users/77/face"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)))
                .thenReturn(ResponseEntity.ok("{}"));

        EmployeeFaceDeviceSyncService.SyncOutcome result = service.syncIfAvailable(employee, 101L);

        assertThat(result).isEqualTo(EmployeeFaceDeviceSyncService.SyncOutcome.SYNCED);
        verify(restTemplate).exchange(
                eq("http://isapi:8081/api/devices/101/users/77/face"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class));
    }

    @Test
    void syncIfAvailable_withoutStoredFace_doesNotCallIsapi() {
        Employee employee = employee();
        when(employeeFaceImageService.getLatestFaceImage(7L)).thenReturn(Optional.empty());

        EmployeeFaceDeviceSyncService.SyncOutcome result = service.syncIfAvailable(employee, 101L);

        assertThat(result).isEqualTo(EmployeeFaceDeviceSyncService.SyncOutcome.NO_FACE);
        verify(restTemplate, never()).exchange(
                any(String.class),
                any(HttpMethod.class),
                any(),
                eq(String.class));
    }

    @Test
    void syncIfAvailable_surfacesAzerbaijaniBridgeValidationMessage() throws Exception {
        Employee employee = employee();
        Path facePath = tempDir.resolve("face.png");
        Files.write(facePath, new byte[]{1, 2, 3});
        when(employeeFaceImageService.getLatestFaceImage(7L))
                .thenReturn(Optional.of(new EmployeeFaceImageService.FaceImageData(facePath, "image/png")));
        when(restTemplate.exchange(
                eq("http://isapi:8081/api/devices/101/users"),
                eq(HttpMethod.GET),
                isNull(),
                eq(String.class)))
                .thenReturn(ResponseEntity.ok("[{\"id\":77,\"employeeNo\":\"1234\"}]"));
        HttpClientErrorException bridgeError = HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST,
                "Bad Request",
                HttpHeaders.EMPTY,
                "{\"message\":\"Şəkil formatı dəstəklənmir. JPG və ya PNG faylı seçin.\"}".getBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        when(restTemplate.exchange(
                eq("http://isapi:8081/api/devices/101/users/77/face"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)))
                .thenThrow(bridgeError);

        assertThatThrownBy(() -> service.syncIfAvailable(employee, 101L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Şəkil formatı dəstəklənmir. JPG və ya PNG faylı seçin.");
    }

    private Employee employee() {
        Employee employee = new Employee();
        employee.setId(7L);
        employee.setEmployeeId("EMP-7");
        employee.setDeviceEmployeeNo("1234");
        return employee;
    }
}
