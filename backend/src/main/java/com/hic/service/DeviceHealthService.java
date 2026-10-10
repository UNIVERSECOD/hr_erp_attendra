package com.hic.service;

import com.hic.dto.DeviceSyncDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.time.Duration;
import java.util.List;

/** Bounded, read-only health polling, independent of long employee/photo sync calls. */
@Service
public class DeviceHealthService {
    private final RestTemplate client;
    private final String url;
    public DeviceHealthService(RestTemplateBuilder builder, @Value("${isapi.base-url}") String base,
                               @Value("${isapi.api-key:}") String apiKey) {
        client = builder.setConnectTimeout(Duration.ofSeconds(2)).setReadTimeout(Duration.ofSeconds(5))
                .defaultHeader("X-API-Key", apiKey).build();
        url = base.replaceAll("/+$", "") + "/api/devices";
    }
    public List<DeviceSyncDTO.IsapiDeviceResponse> getAll() {
        var response = client.exchange(url, HttpMethod.GET, HttpEntity.EMPTY,
                new ParameterizedTypeReference<List<DeviceSyncDTO.IsapiDeviceResponse>>() {});
        return response.getBody() == null ? List.of() : response.getBody();
    }
}
