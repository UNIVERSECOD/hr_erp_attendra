package com.hic.service;

import com.hic.dto.EmployeeDTO;
import com.hic.dto.EmployeeResponseDTO;
import com.hic.dto.EmployeeSearchResultDTO;
import com.hic.exception.BadRequestException;
import com.hic.exception.DeviceSyncException;
import com.hic.exception.ResourceNotFoundException;
import com.hic.model.Department;
import com.hic.model.DeviceConfig;
import com.hic.model.Employee;
import com.hic.model.EmployeeDeviceAccess;
import com.hic.model.Employee.EmploymentStatus;
import com.hic.model.Door;
import com.hic.repository.DepartmentRepository;
import com.hic.repository.DeviceConfigRepository;
import com.hic.repository.DoorRepository;
import com.hic.repository.EmployeeDeviceAccessRepository;
import com.hic.repository.EmployeeRepository;
import com.hic.repository.PositionRepository;
import com.hic.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private PositionRepository positionRepository;

    @Mock
    private EmployeeFaceImageService employeeFaceImageService;

    @Mock
    private DeviceConfigRepository deviceConfigRepository;

    @Mock
    private DoorRepository doorRepository;

    @Mock
    private EmployeeDeviceAccessRepository employeeDeviceAccessRepository;

    @Mock
    private IsapiEmployeeUserSyncService isapiEmployeeUserSyncService;

    @Mock
    private UserScopeService userScopeService;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private ShiftAssignmentService shiftAssignmentService;

    @InjectMocks
    private EmployeeService employeeService;

    private Department testDepartment;
    private Employee testEmployee;
    private EmployeeDTO testEmployeeDTO;

    @BeforeEach
    void setUp() {
        testDepartment = new Department();
        testDepartment.setId(1L);
        testDepartment.setDepartmentName("Engineering");
        testDepartment.setBranchId(1L);

        testEmployee = new Employee();
        testEmployee.setId(1L);
        testEmployee.setEmployeeId("EMP202401001");
        testEmployee.setFirstName("John");
        testEmployee.setLastName("Doe");
        testEmployee.setDepartmentId(1L);
        testEmployee.setEmploymentStatus(EmploymentStatus.ACTIVE);

        testEmployeeDTO = new EmployeeDTO();
        testEmployeeDTO.setFirstName("John");
        testEmployeeDTO.setLastName("Doe");
        testEmployeeDTO.setDepartmentId(1L);
        testEmployeeDTO.setHireDate(LocalDate.now());

        lenient().when(employeeFaceImageService.getLatestEmployeeFacePublicUrl(anyLong()))
                .thenReturn(Optional.empty());
        lenient().when(userScopeService.resolveBranchScope(any())).thenReturn(null);
    }

    @Test
    void getById_existingEmployee_returnsDTO() {
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(testEmployee));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(testDepartment));

        EmployeeResponseDTO result = employeeService.getById(1L);

        assertThat(result).isNotNull();
        assertThat(result.getFirstName()).isEqualTo("John");
        assertThat(result.getLastName()).isEqualTo("Doe");
        assertThat(result.getDepartmentName()).isEqualTo("Engineering");
    }

    @Test
    void getById_nonExistentEmployee_throwsResourceNotFoundException() {
        when(employeeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_validDTO_savesEmployee() {
        when(departmentRepository.existsById(1L)).thenReturn(true);
        when(employeeRepository.count()).thenReturn(0L);
        when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> {
            Employee e = inv.getArgument(0);
            e.setId(1L);
            return e;
        });
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(testDepartment));

        EmployeeResponseDTO result = employeeService.create(testEmployeeDTO);

        assertThat(result).isNotNull();
        assertThat(result.getFirstName()).isEqualTo("John");
        assertThat(result.getEmploymentStatus()).isEqualTo(EmploymentStatus.ACTIVE);
        verify(employeeRepository).save(any(Employee.class));
        verify(isapiEmployeeUserSyncService).syncEmployee(any(Employee.class), anyList());
    }

    @Test
    void create_isapiSyncFails_stillPersistsEmployee() {
        when(departmentRepository.existsById(1L)).thenReturn(true);
        when(employeeRepository.count()).thenReturn(0L);
        when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> {
            Employee e = inv.getArgument(0);
            e.setId(1L);
            return e;
        });
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(testDepartment));
        doThrow(new DeviceSyncException("ISAPI user sync is unavailable"))
                .when(isapiEmployeeUserSyncService).syncEmployee(any(Employee.class), anyList());

        EmployeeResponseDTO result = employeeService.create(testEmployeeDTO);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        verify(employeeRepository).save(any(Employee.class));
        verify(isapiEmployeeUserSyncService).syncEmployee(any(Employee.class), anyList());
    }

    @Test
    void create_duplicateFin_throwsBadRequestException() {
        testEmployeeDTO.setFinNumber("ABC1234567");
        when(departmentRepository.existsById(1L)).thenReturn(true);
        when(employeeRepository.findByFinNumber("ABC1234567")).thenReturn(Optional.of(testEmployee));

        assertThatThrownBy(() -> employeeService.create(testEmployeeDTO))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("FIN kodu");
    }

    @Test
    void create_invalidDepartment_throwsResourceNotFoundException() {
        when(departmentRepository.existsById(1L)).thenReturn(false);

        assertThatThrownBy(() -> employeeService.create(testEmployeeDTO))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_existingEmployee_updatesAndReturns() {
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(testEmployee));
        when(departmentRepository.existsById(1L)).thenReturn(true);
        when(employeeRepository.save(any(Employee.class))).thenReturn(testEmployee);
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(testDepartment));

        testEmployeeDTO.setFirstName("Jane");
        EmployeeResponseDTO result = employeeService.update(1L, testEmployeeDTO);

        assertThat(result).isNotNull();
        verify(employeeRepository).save(any(Employee.class));
    }

    @Test
    void update_preservesSelectedDepartmentAndTimetable() {
        Department newDepartment = new Department();
        newDepartment.setId(2L);
        newDepartment.setDepartmentName("Survey");
        testEmployee.setTimetableId(10L);
        testEmployeeDTO.setDepartmentId(2L);
        testEmployeeDTO.setTimetableId(20L);

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(testEmployee));
        when(departmentRepository.existsById(2L)).thenReturn(true);
        when(employeeRepository.save(any(Employee.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(departmentRepository.findById(2L)).thenReturn(Optional.of(newDepartment));

        EmployeeResponseDTO result = employeeService.update(1L, testEmployeeDTO);

        assertThat(result.getDepartmentId()).isEqualTo(2L);
        assertThat(result.getTimetableId()).isEqualTo(20L);
        verify(shiftAssignmentService).syncScheduleFromEmployee(
                testEmployee, 10L, 20L, com.hic.util.AppTimeZone.today());
    }

    @Test
    void update_isapiSyncFails_stillUpdatesEmployee() {
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(testEmployee));
        when(departmentRepository.existsById(1L)).thenReturn(true);
        when(employeeRepository.save(any(Employee.class))).thenReturn(testEmployee);
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(testDepartment));
        doThrow(new DeviceSyncException("sync unavailable"))
                .when(isapiEmployeeUserSyncService).syncEmployee(any(Employee.class), anyList());

        EmployeeResponseDTO result = employeeService.update(1L, testEmployeeDTO);

        assertThat(result).isNotNull();
        verify(employeeRepository).save(any(Employee.class));
        verify(isapiEmployeeUserSyncService).syncEmployee(any(Employee.class), anyList());
    }

    @Test
    void delete_existingEmployee_deletesSuccessfully() {
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(testEmployee));
        when(employeeDeviceAccessRepository.findByEmployeeId(1L)).thenReturn(List.of());

        employeeService.delete(1L);

        verify(employeeFaceImageService).deleteFaceImages(1L);
        verify(employeeRepository).delete(testEmployee);
    }

    @Test
    void delete_nonExistentEmployee_throwsResourceNotFoundException() {
        when(employeeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.delete(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_removesEmployeeFromEveryBranchDeviceBeforeLocalDelete() {
        testEmployee.setBranchId(1L);
        testEmployee.setDeviceEmployeeNo("1234");
        DeviceConfig firstDevice = device(10L, "101", 1L);
        DeviceConfig secondDevice = device(11L, "102", 1L);
        EmployeeDeviceAccess access = new EmployeeDeviceAccess();
        access.setEmployeeId(1L);
        access.setDeviceConfigId(10L);

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(testEmployee));
        when(employeeDeviceAccessRepository.findByEmployeeId(1L)).thenReturn(List.of(access));
        when(deviceConfigRepository.findAll()).thenReturn(List.of(firstDevice, secondDevice));
        when(deviceConfigRepository.findAllById(List.of(10L, 11L)))
                .thenReturn(List.of(firstDevice, secondDevice));

        employeeService.delete(1L);

        verify(isapiEmployeeUserSyncService).deleteEmployee(testEmployee, List.of(101L, 102L));
        verify(employeeRepository).delete(testEmployee);
    }

    @Test
    void delete_deviceFailureKeepsLocalEmployee() {
        testEmployee.setBranchId(1L);
        DeviceConfig device = device(10L, "101", 1L);

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(testEmployee));
        when(employeeDeviceAccessRepository.findByEmployeeId(1L)).thenReturn(List.of());
        when(deviceConfigRepository.findAll()).thenReturn(List.of(device));
        when(deviceConfigRepository.findAllById(List.of(10L))).thenReturn(List.of(device));
        doThrow(new DeviceSyncException("device offline"))
                .when(isapiEmployeeUserSyncService).deleteEmployee(testEmployee, List.of(101L));

        assertThatThrownBy(() -> employeeService.delete(1L))
                .isInstanceOf(DeviceSyncException.class)
                .hasMessageContaining("offline");
        verify(employeeRepository, never()).delete(any(Employee.class));
        verify(employeeFaceImageService, never()).deleteFaceImages(anyLong());
    }

    @Test
    void getAll_returnsPaginatedResponse() {
        Page<Employee> page = new PageImpl<>(List.of(testEmployee));
        when(employeeRepository.findAll(any(Pageable.class))).thenReturn(page);
        when(departmentRepository.findAllById(anyCollection())).thenReturn(List.of(testDepartment));
        when(positionRepository.findAllById(anyCollection())).thenReturn(Collections.emptyList());

        var result = employeeService.getAll(0, 20, null);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(1L);
    }

    @Test
    void getByDepartment_returnsBatchLoadedDTOs() {
        when(employeeRepository.findByDepartmentId(1L)).thenReturn(List.of(testEmployee));
        when(departmentRepository.findAllById(anyCollection())).thenReturn(List.of(testDepartment));
        when(positionRepository.findAllById(anyCollection())).thenReturn(Collections.emptyList());

        List<EmployeeResponseDTO> result = employeeService.getByDepartment(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDepartmentName()).isEqualTo("Engineering");
        // Verify no extra findById calls (N+1 prevention)
        verify(departmentRepository, never()).findById(anyLong());
        verify(positionRepository, never()).findById(anyLong());
    }

    @Test
    void searchEmployees_matchesFinAndReturnsMinimalResults() {
        testEmployee.setFinNumber("FIN12345");
        when(employeeRepository.searchMinimal(isNull(), eq("FIN12345"), any(Pageable.class)))
                .thenReturn(List.of(testEmployee));
        when(departmentRepository.findAllById(anyCollection())).thenReturn(List.of(testDepartment));

        List<EmployeeSearchResultDTO> result = employeeService.searchEmployees("FIN12345");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEmployeePk()).isEqualTo(1L);
        assertThat(result.get(0).getFinNumber()).isEqualTo("FIN12345");
        assertThat(result.get(0).getDepartmentName()).isEqualTo("Engineering");
    }

    @Test
    void update_branchChangedWithoutDeviceIds_resolvesDevicesByNewBranch() {
        testEmployee.setBranchId(1L);
        testEmployeeDTO.setBranchId(2L);

        Door branchDoor = new Door();
        branchDoor.setId(5L);
        branchDoor.setBranchId(2L);

        com.hic.model.DeviceConfig branchDevice = new com.hic.model.DeviceConfig();
        branchDevice.setId(20L);
        branchDevice.setBranchId(2L);
        branchDevice.setTenantId(testEmployee.getTenantId());

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(testEmployee));
        when(departmentRepository.existsById(1L)).thenReturn(true);
        when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));
        when(employeeDeviceAccessRepository.findByEmployeeId(1L)).thenReturn(List.of());
        when(deviceConfigRepository.findAll()).thenReturn(List.of(branchDevice));
        when(deviceConfigRepository.findAllById(List.of(20L))).thenReturn(List.of(branchDevice));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(testDepartment));

        EmployeeResponseDTO result = employeeService.update(1L, testEmployeeDTO);

        assertThat(result).isNotNull();
        verify(deviceConfigRepository, times(2)).findAll();
        verify(employeeDeviceAccessRepository).deleteByEmployeeId(1L);
    }

    @Test
    void update_doorLinkedDeviceFromWrongBranch_isNotAutoAssigned() {
        testEmployee.setBranchId(1L);
        testEmployeeDTO.setBranchId(1L);

        Door branchDoor = new Door();
        branchDoor.setId(5L);
        branchDoor.setBranchId(1L);

        com.hic.model.DeviceConfig wrongBranchDevice = new com.hic.model.DeviceConfig();
        wrongBranchDevice.setId(10L);
        wrongBranchDevice.setBranchId(2L);
        wrongBranchDevice.setDoorId(5L);
        wrongBranchDevice.setTenantId(testEmployee.getTenantId());

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(testEmployee));
        when(departmentRepository.existsById(1L)).thenReturn(true);
        when(employeeRepository.save(any(Employee.class))).thenReturn(testEmployee);
        when(deviceConfigRepository.findAll()).thenReturn(List.of(wrongBranchDevice));
        when(employeeDeviceAccessRepository.findByEmployeeId(1L)).thenReturn(List.of());
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(testDepartment));

        EmployeeResponseDTO result = employeeService.update(1L, testEmployeeDTO);

        assertThat(result).isNotNull();
        verify(employeeDeviceAccessRepository).deleteByEmployeeId(1L);
        verify(deviceConfigRepository, never()).findAllById(List.of(10L));
    }

    @Test
    void update_syncsEveryDeviceInEmployeesBranch() {
        testEmployee.setBranchId(1L);
        testEmployeeDTO.setBranchId(1L);
        DeviceConfig firstDevice = device(10L, "101", 1L);
        DeviceConfig secondDevice = device(11L, "102", 1L);

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(testEmployee));
        when(departmentRepository.existsById(1L)).thenReturn(true);
        when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));
        when(employeeDeviceAccessRepository.findByEmployeeId(1L)).thenReturn(List.of());
        when(deviceConfigRepository.findAll()).thenReturn(List.of(firstDevice, secondDevice));
        when(deviceConfigRepository.findAllById(List.of(10L, 11L)))
                .thenReturn(List.of(firstDevice, secondDevice));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(testDepartment));

        employeeService.update(1L, testEmployeeDTO);

        verify(isapiEmployeeUserSyncService).syncEmployee(testEmployee, List.of(101L, 102L));
    }

    @Test
    void update_branchChangeRemovesOldDeviceAndSyncsNewDevice() {
        testEmployee.setBranchId(1L);
        testEmployeeDTO.setBranchId(2L);
        DeviceConfig oldDevice = device(10L, "101", 1L);
        DeviceConfig newDevice = device(20L, "202", 2L);

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(testEmployee));
        when(departmentRepository.existsById(1L)).thenReturn(true);
        when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));
        when(employeeDeviceAccessRepository.findByEmployeeId(1L)).thenReturn(List.of());
        when(deviceConfigRepository.findAll()).thenReturn(List.of(oldDevice, newDevice));
        when(deviceConfigRepository.findAllById(List.of(10L))).thenReturn(List.of(oldDevice));
        when(deviceConfigRepository.findAllById(List.of(20L))).thenReturn(List.of(newDevice));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(testDepartment));

        employeeService.update(1L, testEmployeeDTO);

        verify(isapiEmployeeUserSyncService).deleteEmployee(testEmployee, List.of(101L));
        verify(isapiEmployeeUserSyncService).syncEmployee(testEmployee, List.of(202L));
    }

    private DeviceConfig device(Long id, String isapiDeviceId, Long branchId) {
        DeviceConfig device = new DeviceConfig();
        device.setId(id);
        device.setDeviceId(isapiDeviceId);
        device.setBranchId(branchId);
        device.setTenantId(testEmployee.getTenantId());
        return device;
    }
}
