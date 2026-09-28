package com.hic.service;

import com.hic.exception.BadRequestException;
import com.hic.model.Branch;
import com.hic.model.DeviceConfig;
import com.hic.model.Employee;
import com.hic.model.EmployeeDeviceAccess;
import com.hic.repository.BranchRepository;
import com.hic.repository.DeviceConfigRepository;
import com.hic.repository.EmployeeAreaRepository;
import com.hic.repository.EmployeeDeviceAccessRepository;
import com.hic.repository.EmployeeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeAreaAssignmentServiceTest {

    @Mock private EmployeeAreaRepository employeeAreaRepository;
    @Mock private EmployeeDeviceAccessRepository employeeDeviceAccessRepository;
    @Mock private DeviceConfigRepository deviceConfigRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private EmployeeRepository employeeRepository;

    @InjectMocks
    private EmployeeAreaAssignmentService service;

    @Test
    void replaceEmployeeAreas_removesOnlyStaleAreaAccessAndKeepsManualAccess() {
        Employee employee = employee(7L, 1L);
        Branch oldArea = area(10L, 1L);
        Branch newArea = area(20L, 1L);
        DeviceConfig oldDevice = device(100L, 10L, 1L);
        DeviceConfig newDevice = device(200L, 20L, 1L);

        EmployeeDeviceAccess staleAreaAccess = access(
                7L, 100L, EmployeeDeviceAccess.AssignmentSource.AREA, 10L);
        EmployeeDeviceAccess manualAccess = access(
                7L, 999L, EmployeeDeviceAccess.AssignmentSource.MANUAL, null);
        EmployeeDeviceAccess expectedNewAccess = access(
                7L, 200L, EmployeeDeviceAccess.AssignmentSource.AREA, 20L);

        when(branchRepository.findAllById(any())).thenReturn(List.of(newArea));
        when(deviceConfigRepository.findByTenantIdAndBranchId(1L, 20L)).thenReturn(List.of(newDevice));
        when(employeeDeviceAccessRepository.findByEmployeeId(7L))
                .thenReturn(List.of(staleAreaAccess, manualAccess))
                .thenReturn(List.of(staleAreaAccess, manualAccess))
                .thenReturn(List.of(manualAccess, expectedNewAccess));

        EmployeeAreaAssignmentService.AssignmentChange result =
                service.replaceEmployeeAreas(employee, List.of(20L), 20L);

        assertThat(result.deviceIds()).containsExactly(200L, 999L);
        assertThat(result.removedDeviceIds()).containsExactly(100L);
        verify(employeeDeviceAccessRepository).deleteAll(List.of(staleAreaAccess));
        ArgumentCaptor<EmployeeDeviceAccess> createdAccess = ArgumentCaptor.forClass(EmployeeDeviceAccess.class);
        verify(employeeDeviceAccessRepository).save(createdAccess.capture());
        assertThat(createdAccess.getValue().getDeviceConfigId()).isEqualTo(200L);
        assertThat(createdAccess.getValue().getAssignmentSource())
                .isEqualTo(EmployeeDeviceAccess.AssignmentSource.AREA);
        verify(employeeDeviceAccessRepository, never()).delete(manualAccess);
    }

    @Test
    void normalizeAndValidateAreaIds_rejectsAnotherTenantArea() {
        Branch foreignArea = area(20L, 2L);
        when(branchRepository.findAllById(any())).thenReturn(List.of(foreignArea));

        assertThatThrownBy(() -> service.normalizeAndValidateAreaIds(List.of(20L), null, 1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("20");
    }

    private static Employee employee(Long id, Long tenantId) {
        Employee employee = new Employee();
        employee.setId(id);
        employee.setTenantId(tenantId);
        return employee;
    }

    private static Branch area(Long id, Long tenantId) {
        Branch branch = new Branch();
        branch.setId(id);
        branch.setTenantId(tenantId);
        branch.setName("Area " + id);
        return branch;
    }

    private static DeviceConfig device(Long id, Long areaId, Long tenantId) {
        DeviceConfig device = new DeviceConfig();
        device.setId(id);
        device.setBranchId(areaId);
        device.setTenantId(tenantId);
        return device;
    }

    private static EmployeeDeviceAccess access(
            Long employeeId,
            Long deviceId,
            EmployeeDeviceAccess.AssignmentSource source,
            Long sourceAreaId) {
        EmployeeDeviceAccess access = new EmployeeDeviceAccess();
        access.setEmployeeId(employeeId);
        access.setDeviceConfigId(deviceId);
        access.setAssignmentSource(source);
        access.setSourceBranchId(sourceAreaId);
        return access;
    }
}
