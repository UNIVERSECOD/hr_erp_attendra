package com.hic.service;

import com.hic.dto.DeviceSyncDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import java.time.Duration;

/** Separate timeouts prevent offline deletion jobs from blocking other device workflows. */
@Component
public class EmployeeRemovalDeviceClient {
    private final RestTemplate http;
    private final String baseUrl;

    public EmployeeRemovalDeviceClient(RestTemplateBuilder builder,
            @Value("${isapi.base-url}") String baseUrl,
            @Value("${isapi.api-key:}") String apiKey) {
        this.baseUrl = baseUrl.replaceAll("/+$", "") + "/api/devices";
        this.http = builder.setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(20))
                .defaultHeader("X-API-Key", apiKey).build();
    }

    public DeviceSyncDTO.IsapiDeviceResponse device(Long id) {
        return http.getForObject(baseUrl + "/" + id, DeviceSyncDTO.IsapiDeviceResponse.class);
    }

    public DeviceSyncDTO.DeviceStatusDTO status(Long id) {
        return http.getForObject(baseUrl + "/" + id + "/status", DeviceSyncDTO.DeviceStatusDTO.class);
    }

    public void delete(Long bridgeId, String employeeNo) {
        var uri = UriComponentsBuilder.fromHttpUrl(baseUrl).pathSegment(
                bridgeId.toString(), "users", "by-employee-no", employeeNo).build().encode().toUri();
        http.delete(uri);
    }
}
