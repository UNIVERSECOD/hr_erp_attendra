package com.hic.service;

import com.hic.model.FaceData;
import com.hic.repository.FaceDataRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeFaceImageServiceTest {

    @Mock
    private FaceDataRepository faceDataRepository;

    @TempDir
    private Path tempDir;

    @Test
    void getLatestEmployeeFacePublicUrl_returnsUrlOnlyWhenFileExists() throws Exception {
        EmployeeFaceImageService service = new EmployeeFaceImageService(faceDataRepository);
        ReflectionTestUtils.setField(service, "faceImagesDir", tempDir.toString());
        FaceData faceData = new FaceData();
        faceData.setEmployeeId(7L);
        faceData.setFaceImageUrl("employee-7.jpg");
        when(faceDataRepository.findTopByEmployeeIdOrderByCreatedAtDesc(7L))
                .thenReturn(Optional.of(faceData));

        assertThat(service.getLatestEmployeeFacePublicUrl(7L)).isEmpty();

        Files.write(tempDir.resolve("employee-7.jpg"), new byte[]{1, 2, 3});

        assertThat(service.getLatestEmployeeFacePublicUrl(7L))
                .contains("/api/faces/employee/7/image");
    }
}
