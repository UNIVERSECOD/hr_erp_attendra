package com.hic.controller;

import com.hic.exception.GlobalExceptionHandler;
import com.hic.service.EmployeeFaceImageService;
import com.hic.service.EmployeeFaceSynchronizationService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// Real embedded Tomcat parses the multipart body; MockMvc alone bypasses its limits.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = EmployeeUploadLimitIntegrationTest.UploadApplication.class)
class EmployeeUploadLimitIntegrationTest {
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class,
            SecurityAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class})
    @Import({EmployeeFaceController.class, GlobalExceptionHandler.class})
    static class UploadApplication { }

    @LocalServerPort
    private int port;

    @MockBean
    private EmployeeFaceImageService images;

    @MockBean
    private EmployeeFaceSynchronizationService synchronization;

    @Test
    void exactlyFiveMegabytesReachesUploadService() throws Exception {
        // Tomcat deletes temporary parts after the response; inspect size during the call.
        when(synchronization.saveAndSync(eq(7L), any())).thenAnswer(invocation -> {
            org.springframework.web.multipart.MultipartFile file = invocation.getArgument(1);
            assertThat(file.getSize()).isEqualTo(5 * 1024 * 1024);
            return null;
        });
        var response = upload(5 * 1024 * 1024);

        assertThat(response.statusCode()).isEqualTo(200);
        verify(synchronization).saveAndSync(eq(7L), any());
    }

    @Test
    void oneByteOverFileLimitReturnsLocalized413WithoutSaving() throws Exception {
        assertRejected(upload(5 * 1024 * 1024 + 1));
    }

    @Test
    void requestOverAggregateLimitReturnsLocalized413WithoutSaving() throws Exception {
        assertRejected(upload(6 * 1024 * 1024 + 1));
    }

    private HttpResponse<String> upload(int size) throws Exception {
        String boundary = "AttendraUploadLimitTest";
        var body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"fixture.png\""
                + "\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        body.write(new byte[size]);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/faces/employee/7/image"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private void assertRejected(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.headers().firstValue("content-type")).hasValueSatisfying(type -> assertThat(type).contains("application/json"));
        assertThat(response.body()).contains("\"success\":false", "Fayl ən çox 5 MB ola bilər");
        verifyNoInteractions(images, synchronization);
    }
}
