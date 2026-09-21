package com.hic.service;

import com.hic.dto.IsapiUserInfoCreateRequestDTO;
import com.hic.exception.DeviceSyncException;
import com.hic.exception.UpstreamApiException;
import com.hic.model.Employee;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class IsapiEmployeeUserSyncService {

    private static final String DEFAULT_DEVICE_BASE_URL = "http://192.168.0.200";
    private static final String ISAPI_SERVICE_DEVICE_USER_PATH_TEMPLATE = "/api/devices/%d/users";

    private final RestTemplate restTemplate;

    @Value("${isapi.user-info-record.base-url:}")
    private String userInfoRecordBaseUrl;

    @Value("${isapi.base-url:}")
    private String isapiBaseUrl;

    @Value("${isapi.user-info-record.path:/ISAPI/AccessControl/UserInfo/Record}")
    private String userInfoRecordPath;

    @Value("${isapi.user-info-record.security:1}")
    private String security;

    @Value("${isapi.user-info-record.iv:}")
    private String iv;

    @Value("${isapi.user-info-record.door-right:1}")
    private String doorRight;

    @Value("${isapi.user-info-record.door-no:1}")
    private int doorNo;

    @Value("${isapi.user-info-record.plan-template-no:1}")
    private String planTemplateNo;

    @Value("${isapi.user-sync.device-id:1}")
    private long userSyncDeviceId;

    @Value("${isapi.user-info-record.username:}")
    private String username;

    @Value("${isapi.user-info-record.password:}")
    private String password;

    public void syncEmployee(Employee employee) {
        syncEmployee(employee, List.of());
    }

    public void syncEmployee(Employee employee, List<Long> deviceConfigIds) {
        List<Long> assignedDeviceIds = deviceConfigIds == null
                ? List.of()
                : deviceConfigIds.stream().filter(id -> id != null && id > 0).distinct().toList();

        if (!assignedDeviceIds.isEmpty()) {
            if (!StringUtils.hasText(isapiBaseUrl)) {
                log.warn("Skipping device-scoped sync for employee {} because isapi.base-url is not configured", employee.getEmployeeId());
                return;
            }
            List<String> failures = new ArrayList<>();
            for (Long deviceId : assignedDeviceIds) {
                try {
                    syncEmployeeViaIsapiService(employee, deviceId);
                } catch (RuntimeException ex) {
                    failures.add(deviceId + ": " + ex.getMessage());
                }
            }
            throwIfDeviceOperationsFailed("sync", employee, failures);
            return;
        }

        IsapiUserInfoCreateRequestDTO request = buildRequest(employee);
        HttpHeaders headers = buildHeaders();
        String url = buildUserInfoRecordUrl();
        log.info("Syncing employee {} to ISAPI device endpoint {}", employee.getEmployeeId(), url);

        try {
            HttpEntity<IsapiUserInfoCreateRequestDTO> entity = new HttpEntity<>(request, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw buildUpstreamApiException(url, response.getStatusCode(), response.getBody());
            }
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode().value() == 404 && StringUtils.hasText(isapiBaseUrl)) {
                log.warn("Direct ISAPI UserInfo endpoint returned 404 for {}. Retrying via isapi service API.", url);
                syncEmployeeViaIsapiService(employee, userSyncDeviceId);
                return;
            }
            throw buildUpstreamApiException(url, ex.getStatusCode(), ex.getResponseBodyAsString());
        } catch (ResourceAccessException ex) {
            log.error("ISAPI user sync is unavailable for employee {} via {}", employee.getEmployeeId(), url, ex);
            throw new DeviceSyncException("ISAPI user sync is unavailable for " + url, ex);
        }
    }

    public void deleteEmployee(Employee employee, List<Long> deviceIds) {
        List<Long> targetDeviceIds = deviceIds == null
                ? List.of()
                : deviceIds.stream().filter(id -> id != null && id > 0).distinct().toList();
        if (targetDeviceIds.isEmpty()) {
            return;
        }
        if (!StringUtils.hasText(isapiBaseUrl)) {
            throw new DeviceSyncException("ISAPI service base URL is not configured");
        }

        List<String> failures = new ArrayList<>();
        for (Long deviceId : targetDeviceIds) {
            try {
                deleteEmployeeViaIsapiService(employee, deviceId);
            } catch (RuntimeException ex) {
                failures.add(deviceId + ": " + ex.getMessage());
            }
        }
        throwIfDeviceOperationsFailed("delete", employee, failures);
    }

    private IsapiUserInfoCreateRequestDTO buildRequest(Employee employee) {
        LocalDate beginDate = employee.getHireDate() != null ? employee.getHireDate() : LocalDate.now();
        LocalDateTime beginTime = beginDate.atStartOfDay();
        LocalDateTime endTime = beginTime.plusYears(10).minusSeconds(1);
        String fullName = (employee.getFirstName() + " " + employee.getLastName()).trim();
        String normalizedGender = HikvisionPayloadNormalizer.normalizeGender(employee.getGender());
        IsapiUserInfoCreateRequestDTO.ValidityDTO validity = new IsapiUserInfoCreateRequestDTO.ValidityDTO(
                true,
                beginTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                endTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "local"
        );
        IsapiUserInfoCreateRequestDTO.RightPlanDTO rightPlan = new IsapiUserInfoCreateRequestDTO.RightPlanDTO(doorNo, planTemplateNo);
        IsapiUserInfoCreateRequestDTO.UserInfoDTO userInfo = new IsapiUserInfoCreateRequestDTO.UserInfoDTO(
                resolveDevicePersonNo(employee),
                fullName,
                "normal",
                normalizedGender,
                false,
                0,
                validity,
                doorRight,
                List.of(rightPlan),
                null
        );
        return new IsapiUserInfoCreateRequestDTO(userInfo);
    }

    private String buildUserInfoRecordUrl() {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(trimTrailingSlash(resolveUserInfoRecordBaseUrl()) + ensureLeadingSlash(userInfoRecordPath))
                .queryParam("format", "json")
                .queryParam("security", security);
        if (StringUtils.hasText(iv)) {
            builder.queryParam("iv", iv);
        }
        return builder.toUriString();
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (StringUtils.hasText(username) && StringUtils.hasText(password)) {
            String token = Base64.getEncoder()
                    .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
            headers.set(HttpHeaders.AUTHORIZATION, "Basic " + token);
        }
        return headers;
    }

    private String trimTrailingSlash(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String ensureLeadingSlash(String value) {
        return value.startsWith("/") ? value : "/" + value;
    }

    private String resolveUserInfoRecordBaseUrl() {
        if (StringUtils.hasText(userInfoRecordBaseUrl)) {
            return userInfoRecordBaseUrl;
        }
        if (StringUtils.hasText(isapiBaseUrl)) {
            return isapiBaseUrl;
        }
        return DEFAULT_DEVICE_BASE_URL;
    }

    private void syncEmployeeViaIsapiService(Employee employee, long deviceId) {
        String basePath = String.format(ISAPI_SERVICE_DEVICE_USER_PATH_TEMPLATE, deviceId);
        String baseUrl = trimTrailingSlash(isapiBaseUrl) + basePath;
        DeviceUserCreateRequest createBody = buildDeviceUserCreateRequest(employee);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        try {
            HttpEntity<Object> entity = new HttpEntity<>(createBody, headers);
            ResponseEntity<String> response = restTemplate.exchange(baseUrl, HttpMethod.POST, entity, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw buildUpstreamApiException(baseUrl, response.getStatusCode(), response.getBody());
            }
            log.info("Employee {} synced via isapi service endpoint {} (deviceConfigId={}, mode=CREATE)",
                    employee.getEmployeeId(), baseUrl, deviceId);
        } catch (HttpStatusCodeException ex) {
            throw buildUpstreamApiException(baseUrl, ex.getStatusCode(), ex.getResponseBodyAsString());
        } catch (ResourceAccessException ex) {
            log.error("ISAPI service sync is unavailable for employee {} via {}", employee.getEmployeeId(), baseUrl, ex);
            throw new DeviceSyncException("ISAPI service sync is unavailable for " + baseUrl, ex);
        }
    }

    private void deleteEmployeeViaIsapiService(Employee employee, long deviceId) {
        String employeeNo = resolveDevicePersonNo(employee);
        String usersUrl = trimTrailingSlash(isapiBaseUrl)
                + String.format(ISAPI_SERVICE_DEVICE_USER_PATH_TEMPLATE, deviceId);
        String url = UriComponentsBuilder.fromHttpUrl(usersUrl)
                .pathSegment("by-employee-no", employeeNo)
                .build()
                .encode()
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.DELETE, new HttpEntity<>(headers), String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw buildUpstreamApiException(url, response.getStatusCode(), response.getBody());
            }
            log.info("Employee {} deleted via isapi service endpoint {} (deviceId={})",
                    employee.getEmployeeId(), url, deviceId);
        } catch (HttpStatusCodeException ex) {
            throw buildUpstreamApiException(url, ex.getStatusCode(), ex.getResponseBodyAsString());
        } catch (ResourceAccessException ex) {
            log.error("ISAPI service delete is unavailable for employee {} via {}",
                    employee.getEmployeeId(), url, ex);
            throw new DeviceSyncException("ISAPI service delete is unavailable for " + url, ex);
        }
    }

    private void throwIfDeviceOperationsFailed(String operation, Employee employee, List<String> failures) {
        if (failures.isEmpty()) {
            return;
        }
        throw new DeviceSyncException("Employee " + resolveDevicePersonNo(employee)
                + " could not complete device " + operation + " on: " + String.join("; ", failures));
    }

    private DeviceUserCreateRequest buildDeviceUserCreateRequest(Employee employee) {
        LocalDate beginDate = employee.getHireDate() != null ? employee.getHireDate() : LocalDate.now();
        LocalDateTime beginTime = beginDate.atStartOfDay();
        LocalDateTime endTime = beginTime.plusYears(10).minusSeconds(1);
        String fullName = (employee.getFirstName() + " " + employee.getLastName()).trim();
        String normalizedGender = HikvisionPayloadNormalizer.normalizeGender(employee.getGender());

        return new DeviceUserCreateRequest(
                resolveDevicePersonNo(employee),
                fullName,
                "normal",
                normalizedGender,
                beginTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                endTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        );
    }

    /** Person ID written to / read from the device — never the branch-prefixed HR code. */
    private static String resolveDevicePersonNo(Employee employee) {
        if (StringUtils.hasText(employee.getDeviceEmployeeNo())) {
            return employee.getDeviceEmployeeNo().trim();
        }
        return employee.getEmployeeId();
    }

    private UpstreamApiException buildUpstreamApiException(String url, HttpStatusCode statusCode, String responseBody) {
        StringBuilder message = new StringBuilder("ISAPI user sync failed with HTTP ")
                .append(statusCode.value())
                .append(" for ")
                .append(url);
        if (statusCode.value() == 404) {
            message.append(". Verify isapi.user-info-record.base-url points to the device host and not the backend host.");
        }
        if (StringUtils.hasText(responseBody)) {
            message.append(" Response: ").append(responseBody);
        }
        log.warn("{}", message);
        return new UpstreamApiException(statusCode, message.toString());
    }

    private record DeviceUserCreateRequest(
            String employeeNo,
            String name,
            String userType,
            String gender,
            String beginTime,
            String endTime
    ) {
    }

}
