package com.hic.service;

import com.hic.dto.DeviceEmployeeAssignmentDTO.AssignmentView;
import com.hic.dto.DeviceEmployeeAssignmentDTO.SyncResult;
import com.hic.dto.DeviceEmployeeAssignmentDTO.UpdateRequest;
import com.hic.model.Branch;
import com.hic.model.DeviceConfig;
import com.hic.model.Employee;
import com.hic.model.EmployeeArea;
import com.hic.model.EmployeeDeviceAccess;
import com.hic.repository.BranchRepository;
import com.hic.repository.DeviceConfigRepository;
import com.hic.repository.EmployeeAreaRepository;
import com.hic.repository.EmployeeDeviceAccessRepository;
import com.hic.repository.EmployeeRepository;
import com.hic.util.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceEmployeeAssignmentServiceTest {

    @Mock private DeviceConfigRepository deviceConfigRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private EmployeeAreaRepository employeeAreaRepository;
    @Mock private EmployeeDeviceAccessRepository employeeDeviceAccessRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private EmployeeAreaAssignmentService employeeAreaAssignmentService;
    @Mock private IsapiEmployeeUserSyncService isapiEmployeeUserSyncService;
    @Mock private EmployeeFaceDeviceSyncService employeeFaceDeviceSyncService;

    @InjectMocks
    private DeviceEmployeeAssignmentService service;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void getAssignments_distinguishesAreaAndManualAssignments() {
        DeviceConfig device = device();
        Employee areaEmployee = employee(1L, "EMP-1", "Leyla");
        Employee manualEmployee = employee(2L, "EMP-2", "Ismayil");
        EmployeeDeviceAccess manualAccess = new EmployeeDeviceAccess();
        manualAccess.setEmployeeId(2L);
        manualAccess.setDeviceConfigId(5L);
        manualAccess.setAssignmentSource(EmployeeDeviceAccess.AssignmentSource.MANUAL);

        Branch area = new Branch();
        area.setId(10L);
        area.setName("Head Office");
        when(deviceConfigRepository.findById(5L)).thenReturn(Optional.of(device));
        when(employeeRepository.findAllByTenantIdOrderByFirstNameAscLastNameAsc(1L))
                .thenReturn(List.of(areaEmployee, manualEmployee));
        when(employeeAreaAssignmentService.loadAreaBatch(List.of(1L, 2L)))
                .thenReturn(new EmployeeAreaAssignmentService.AreaBatch(
                        Map.of(1L, List.of(10L), 2L, List.of(20L)),
                        Map.of(1L, List.of("Head Office"), 2L, List.of("Site B"))));
        when(employeeDeviceAccessRepository.findByDeviceConfigId(5L)).thenReturn(List.of(manualAccess));
        when(branchRepository.findById(10L)).thenReturn(Optional.of(area));

        AssignmentView result = service.getAssignments(5L);

        assertThat(result.getEmployees()).hasSize(2);
        assertThat(result.getEmployees().get(0).isAreaAssigned()).isTrue();
        assertThat(result.getEmployees().get(0).isAssigned()).isTrue();
        assertThat(result.getEmployees().get(1).isManuallyAssigned()).isTrue();
        assertThat(result.getEmployees().get(1).isAssigned()).isTrue();
    }

    @Test
    void syncEmployees_continuesAfterOneEmployeeFails() {
        DeviceConfig device = device();
        Employee first = employee(1L, "EMP-1", "Leyla");
        Employee second = employee(2L, "EMP-2", "Ismayil");
        when(deviceConfigRepository.findById(5L)).thenReturn(Optional.of(device));
        when(employeeAreaAssignmentService.reconcileDeviceAreaAccess(device)).thenReturn(List.of(1L, 2L));
        when(employeeRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(first, second));
        when(employeeFaceDeviceSyncService.syncIfAvailable(first, 101L))
                .thenReturn(EmployeeFaceDeviceSyncService.SyncOutcome.NO_FACE);
        doThrow(new IllegalStateException("device rejected user"))
                .when(isapiEmployeeUserSyncService).syncEmployee(second, List.of(101L));

        SyncResult result = service.syncEmployees(5L);

        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(result.getSucceeded()).isEqualTo(1);
        assertThat(result.getFailed()).isEqualTo(1);
        assertThat(result.getFacesSkipped()).isEqualTo(1);
        assertThat(result.getErrors()).singleElement().asString().contains("EMP-2");
        verify(isapiEmployeeUserSyncService).syncEmployee(first, List.of(101L));
        verify(isapiEmployeeUserSyncService).syncEmployee(second, List.of(101L));
    }

    @Test
    void updateManualAssignments_keepsExplicitAccessWhenEmployeeAlsoBelongsToDeviceArea() {
        DeviceConfig device = device();
        Employee employee = employee(1L, "EMP-1", "Leyla");
        EmployeeArea membership = new EmployeeArea();
        membership.setTenantId(1L);
        membership.setEmployeeId(1L);
        membership.setBranchId(10L);

        EmployeeDeviceAccess manualAccess = new EmployeeDeviceAccess();
        manualAccess.setTenantId(1L);
        manualAccess.setEmployeeId(1L);
        manualAccess.setDeviceConfigId(5L);
        manualAccess.setAssignmentSource(EmployeeDeviceAccess.AssignmentSource.MANUAL);

        Branch area = new Branch();
        area.setId(10L);
        area.setName("Head Office");

        when(deviceConfigRepository.findById(5L)).thenReturn(Optional.of(device));
        when(employeeAreaAssignmentService.reconcileDeviceAreaAccess(device)).thenReturn(List.of(1L));
        when(employeeRepository.findAllById(any())).thenReturn(List.of(employee));
        when(employeeAreaRepository.findByBranchId(10L)).thenReturn(List.of(membership));
        when(employeeDeviceAccessRepository.findByDeviceConfigId(5L)).thenReturn(List.of(manualAccess));
        when(employeeRepository.findAllByTenantIdOrderByFirstNameAscLastNameAsc(1L))
                .thenReturn(List.of(employee));
        when(employeeAreaAssignmentService.loadAreaBatch(List.of(1L)))
                .thenReturn(new EmployeeAreaAssignmentService.AreaBatch(
                        Map.of(1L, List.of(10L)),
                        Map.of(1L, List.of("Head Office"))));
        when(branchRepository.findById(10L)).thenReturn(Optional.of(area));

        AssignmentView result = service.updateManualAssignments(5L, new UpdateRequest(List.of(1L)));

        assertThat(manualAccess.getAssignmentSource())
                .isEqualTo(EmployeeDeviceAccess.AssignmentSource.MANUAL);
        assertThat(manualAccess.getSourceBranchId()).isNull();
        assertThat(result.getEmployees()).singleElement().satisfies(option -> {
            assertThat(option.isAreaAssigned()).isTrue();
            assertThat(option.isManuallyAssigned()).isTrue();
        });
    }

    private static DeviceConfig device() {
        DeviceConfig device = new DeviceConfig();
        device.setId(5L);
        device.setDeviceId("101");
        device.setDeviceName("Entry");
        device.setBranchId(10L);
        device.setTenantId(1L);
        return device;
    }

    private static Employee employee(Long id, String code, String firstName) {
        Employee employee = new Employee();
        employee.setId(id);
        employee.setTenantId(1L);
        employee.setEmployeeId(code);
        employee.setFirstName(firstName);
        employee.setLastName("Test");
        employee.setEmploymentStatus(Employee.EmploymentStatus.ACTIVE);
        return employee;
    }
}
