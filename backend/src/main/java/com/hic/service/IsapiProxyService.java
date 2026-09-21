package com.hic.service;

import com.hic.exception.DeviceSyncException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IsapiProxyService {

    private static final List<String> EXCLUDED_RESPONSE_HEADERS = List.of("transfer-encoding", "content-length", "connection", "keep-alive");

    private final RestTemplate restTemplate;

    @Value("${isapi.base-url}")
    private String isapiBaseUrl;

    public ResponseEntity<String> forward(HttpMethod method, String path, HttpServletRequest request, String body) {
        String targetUrl = buildTargetUrl(path, request);
        HttpEntity<String> entity = new HttpEntity<>(body, buildForwardHeaders(request));

        try {
            ResponseEntity<String> upstream = restTemplate.exchange(targetUrl, method, entity, String.class);
            return ResponseEntity.status(upstream.getStatusCode())
                    .headers(filterResponseHeaders(upstream.getHeaders()))
                    .body(upstream.getBody());
        } catch (HttpStatusCodeException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .headers(filterResponseHeaders(ex.getResponseHeaders()))
                    .body(ex.getResponseBodyAsString());
        } catch (ResourceAccessException ex) {
            throw new DeviceSyncException("ISAPI service is unavailable");
        }
    }

    public ResponseEntity<String> forwardMultipart(String path, MultipartHttpServletRequest request) {
        String targetUrl = buildTargetUrl(path, request);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

        Iterator<String> fileNames = request.getFileNames();
        while (fileNames.hasNext()) {
            String name = fileNames.next();
            List<MultipartFile> files = request.getFiles(name);
            for (MultipartFile file : files) {
                body.add(name, asResource(file));
            }
        }
        request.getParameterMap().forEach((name, values) -> {
            for (String value : values) {
                body.add(name, value);
            }
        });

        HttpHeaders headers = buildForwardHeaders(request);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        try {
            ResponseEntity<String> upstream = restTemplate.exchange(targetUrl, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
            return ResponseEntity.status(upstream.getStatusCode())
                    .headers(filterResponseHeaders(upstream.getHeaders()))
                    .body(upstream.getBody());
        } catch (HttpStatusCodeException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .headers(filterResponseHeaders(ex.getResponseHeaders()))
                    .body(ex.getResponseBodyAsString());
        } catch (ResourceAccessException ex) {
            throw new DeviceSyncException("ISAPI service is unavailable");
        }
    }

    private String buildTargetUrl(String path, HttpServletRequest request) {
        String baseUrl = trimTrailingSlash(isapiBaseUrl) + path;
        String queryString = request.getQueryString();
        if (!StringUtils.hasText(queryString)) {
            return baseUrl;
        }
        return UriComponentsBuilder.fromHttpUrl(baseUrl).query(queryString).toUriString();
    }

    private HttpHeaders buildForwardHeaders(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        Enumeration<String> names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            if ("host".equalsIgnoreCase(name) || "content-length".equalsIgnoreCase(name)) {
                continue;
            }
            Enumeration<String> values = request.getHeaders(name);
            while (values != null && values.hasMoreElements()) {
                headers.add(name, values.nextElement());
            }
        }
        return headers;
    }

    private HttpHeaders filterResponseHeaders(HttpHeaders upstreamHeaders) {
        if (upstreamHeaders == null || upstreamHeaders.isEmpty()) {
            return new HttpHeaders();
        }
        HttpHeaders filtered = new HttpHeaders();
        upstreamHeaders.forEach((name, values) -> {
            if (EXCLUDED_RESPONSE_HEADERS.contains(name.toLowerCase())) {
                return;
            }
            filtered.put(name, values);
        });
        return filtered;
    }

    private String trimTrailingSlash(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private ByteArrayResource asResource(MultipartFile file) {
        try {
            return new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };
        } catch (Exception ex) {
            throw new DeviceSyncException("Failed to read multipart file");
        }
    }
}
