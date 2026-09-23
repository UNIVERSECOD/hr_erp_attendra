package com.hic.controller;

import com.hic.service.EmployeeFaceImageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmployeeFaceControllerTest {

    @Mock
    private EmployeeFaceImageService employeeFaceImageService;

    @InjectMocks
    private EmployeeFaceController controller;

    @Test
    void deleteEmployeeFaceImage_removesImageAndReturnsNoContent() {
        var response = controller.deleteEmployeeFaceImage(55L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(employeeFaceImageService).deleteFaceImages(55L);
    }
}
