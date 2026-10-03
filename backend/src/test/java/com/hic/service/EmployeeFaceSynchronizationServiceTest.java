package com.hic.service;

import com.hic.dto.DeviceEmployeeAssignmentDTO.EmployeeSyncResult;
import com.hic.exception.ResourceNotFoundException;
import com.hic.model.Employee;
import com.hic.repository.EmployeeRepository;
import com.hic.util.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeFaceSynchronizationServiceTest {

    @Mock private EmployeeRepository employeeRepository;
    @Mock private EmployeeFaceImageService employeeFaceImageService;
    @Mock private DeviceEmployeeAssignmentService deviceEmployeeAssignmentService;
    @Mock private MultipartFile file;

    private EmployeeFaceSynchronizationService service;

    @BeforeEach
    void setUp() {
        service = new EmployeeFaceSynchronizationService(
                employeeRepository,
                employeeFaceImageService,
                deviceEmployeeAssignmentService);
        TenantContext.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void saveAndSync_persistsProfilePhotoBeforeStartingDeviceSync() {
        Employee employee = new Employee();
        employee.setId(7L);
        employee.setTenantId(1L);
        EmployeeSyncResult expected = new EmployeeSyncResult(7L, 1, 1, 1, 0, 0, new ArrayList<>());
        when(employeeRepository.findById(7L)).thenReturn(Optional.of(employee));
        when(deviceEmployeeAssignmentService.syncEmployee(7L)).thenReturn(expected);

        EmployeeSyncResult result = service.saveAndSync(7L, file);

        assertThat(result).isSameAs(expected);
        InOrder order = inOrder(employeeFaceImageService, deviceEmployeeAssignmentService);
        order.verify(employeeFaceImageService).saveFaceImage(7L, file);
        order.verify(deviceEmployeeAssignmentService).syncEmployee(7L);
    }

    @Test
    void saveAndSync_rejectsEmployeeFromAnotherTenantBeforeSavingPhoto() {
        Employee employee = new Employee();
        employee.setId(7L);
        employee.setTenantId(2L);
        when(employeeRepository.findById(7L)).thenReturn(Optional.of(employee));

        assertThatThrownBy(() -> service.saveAndSync(7L, file))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(employeeFaceImageService, never()).saveFaceImage(7L, file);
        verify(deviceEmployeeAssignmentService, never()).syncEmployee(7L);
    }
}
