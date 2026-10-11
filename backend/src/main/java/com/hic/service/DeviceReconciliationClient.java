package com.hic.service;

import com.hic.dto.DeviceSyncDTO;
import com.hic.model.Employee;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import java.nio.file.Files;
import java.time.*;
import java.util.*;

@Component
public class DeviceReconciliationClient {
    public record Person(String employeeNo, String name, String userType, String gender, String beginTime, String endTime) {}
    private final RestTemplate http;
    private final String base;
    private final EmployeeFaceImageService faces;

    public DeviceReconciliationClient(RestTemplateBuilder builder, EmployeeFaceImageService faces,
            @Value("${isapi.base-url}") String base, @Value("${isapi.api-key:}") String key) {
        this.http = builder.setConnectTimeout(Duration.ofSeconds(5)).setReadTimeout(Duration.ofSeconds(20))
                .defaultHeader("X-API-Key", key).build();
        this.base = base.replaceAll("/+$", "") + "/api/devices/";
        this.faces = faces;
    }

    public DeviceSyncDTO.IsapiDeviceResponse device(long id) {
        return http.getForObject(base + id, DeviceSyncDTO.IsapiDeviceResponse.class);
    }
    public DeviceSyncDTO.DeviceStatusDTO status(long id) {
        return http.getForObject(base + id + "/status", DeviceSyncDTO.DeviceStatusDTO.class);
    }
    public List<Person> snapshot(long id) {
        Person[] persons = http.getForObject(base + id + "/users/reconciliation-snapshot", Person[].class);
        if (persons == null) throw new IllegalStateException("Device snapshot unavailable");
        Set<String> seen = new HashSet<>();
        for (Person p : persons) {
            if (p == null || p.employeeNo() == null || p.employeeNo().isBlank() || !seen.add(p.employeeNo()))
                throw new IllegalStateException("Invalid device snapshot");
        }
        return Arrays.stream(persons).sorted(Comparator.comparing(Person::employeeNo)).toList();
    }
    public Long add(long id, Employee employee) {
        LocalDate begin = employee.getHireDate() == null ? LocalDate.now(ZoneId.of("Asia/Baku")) : employee.getHireDate();
        Map<String, Object> body = Map.of("employeeNo", DeviceReconciliationService.number(employee),
                "name", DeviceReconciliationService.name(employee), "userType", "normal",
                "gender", Objects.toString(HikvisionPayloadNormalizer.normalizeGender(employee.getGender()), "unknown"),
                "beginTime", begin.atStartOfDay().toString(), "endTime", begin.plusYears(10).atStartOfDay().minusSeconds(1).toString());
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        JsonNode response = http.postForObject(base + id + "/users", new HttpEntity<>(body, headers), JsonNode.class);
        return response != null && response.path("id").canConvertToLong() ? response.path("id").asLong() : null;
    }
    public String addFace(long id, Long userId, Employee employee) throws Exception {
        var face = faces.getLatestFaceImage(employee.getId());
        if (face.isEmpty()) return "Şəkil yoxdur";
        if (userId == null) throw new IllegalStateException("Device user id unavailable");
        byte[] bytes = Files.readAllBytes(face.get().path());
        ByteArrayResource resource = new ByteArrayResource(bytes) {
            @Override public String getFilename() { return "face.jpg"; }
        };
        var partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.parseMediaType(face.get().contentType()));
        var body = new LinkedMultiValueMap<String, Object>();
        body.add("file", new HttpEntity<>(resource, partHeaders));
        var headers = new HttpHeaders(); headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        http.postForEntity(base + id + "/users/" + userId + "/face", new HttpEntity<>(body, headers), String.class);
        return "Şəkil göndərildi";
    }
    public void remove(long id, String number) {
        http.delete(UriComponentsBuilder.fromHttpUrl(base).pathSegment(Long.toString(id), "users", "reconciliation", "by-employee-no", number)
                .build().encode().toUri());
    }
}
