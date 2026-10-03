package com.hic.controller;

import com.hic.dto.DeviceEmployeeAssignmentDTO.EmployeeSyncResult;
import com.hic.service.EmployeeFaceImageService;
import com.hic.service.EmployeeFaceSynchronizationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeFaceControllerTest {

    @Mock
    private EmployeeFaceImageService employeeFaceImageService;

    @Mock
    private EmployeeFaceSynchronizationService employeeFaceSynchronizationService;

    @InjectMocks
    private EmployeeFaceController controller;

    @Test
    void uploadEmployeeFaceImage_returnsDeviceSynchronizationResult() {
        MockMultipartFile file = new MockMultipartFile("file", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        EmployeeSyncResult syncResult = new EmployeeSyncResult(55L, 2, 2, 2, 0, 0, new ArrayList<>());
        when(employeeFaceSynchronizationService.saveAndSync(55L, file)).thenReturn(syncResult);

        var response = controller.uploadEmployeeFaceImage(55L, file);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isSameAs(syncResult);
        verify(employeeFaceSynchronizationService).saveAndSync(55L, file);
    }

    @Test
    void deleteEmployeeFaceImage_removesImageAndReturnsNoContent() {
        var response = controller.deleteEmployeeFaceImage(55L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(employeeFaceImageService).deleteFaceImages(55L);
    }
}
