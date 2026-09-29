package com.hic.service;

import com.hic.dto.DataImportResultDTO;
import com.hic.dto.EmployeeDTO;
import com.hic.model.Branch;
import com.hic.model.Department;
import com.hic.model.Employee;
import com.hic.model.Position;
import com.hic.model.Timetable;
import com.hic.repository.BranchRepository;
import com.hic.repository.DepartmentRepository;
import com.hic.repository.EmployeeRepository;
import com.hic.repository.PositionRepository;
import com.hic.repository.TenantRepository;
import com.hic.repository.TimetableRepository;
import com.hic.util.TabularFileUtil;
import com.hic.util.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MasterDataTransferServiceTest {

    @Mock private BranchRepository branchRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private PositionRepository positionRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private TimetableRepository timetableRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private EmployeeService employeeService;
    @Mock private EmployeeAreaAssignmentService employeeAreaAssignmentService;
    @Mock private UserScopeService userScopeService;

    private MasterDataTransferService service;
    private Branch branch;
    private Department department;
    private Timetable timetable;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(7L);
        service = new MasterDataTransferService(
                new TabularFileUtil(),
                branchRepository,
                departmentRepository,
                positionRepository,
                employeeRepository,
                timetableRepository,
                tenantRepository,
                employeeService,
                employeeAreaAssignmentService,
                userScopeService);

        branch = new Branch();
        branch.setId(1L);
        branch.setTenantId(7L);
        branch.setName("Baş ofis");
        branch.setCode("HO");

        department = new Department();
        department.setId(2L);
        department.setTenantId(7L);
        department.setBranchId(1L);
        department.setDepartmentName("İnsan Resursları");

        timetable = new Timetable();
        timetable.setId(3L);
        timetable.setTenantId(7L);
        timetable.setName("Standart qrafik");
        timetable.setStartTime(LocalTime.of(8, 0));
        timetable.setEndTime(LocalTime.of(17, 0));
        timetable.setShiftType("STANDARD");

        when(userScopeService.resolveBranchScope(null)).thenReturn(null);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void importsEmployeeWithoutCallingDeviceLayer() {
        when(branchRepository.findByTenantId(7L)).thenReturn(List.of(branch));
        when(departmentRepository.findByTenantId(7L)).thenReturn(List.of(department));
        when(positionRepository.findByTenantId(7L)).thenReturn(List.of());
        when(timetableRepository.findByTenantId(7L)).thenReturn(List.of(timetable));
        when(employeeRepository.findAllByTenantIdOrderByFirstNameAscLastNameAsc(7L)).thenReturn(List.of());
        when(tenantRepository.findById(7L)).thenReturn(Optional.empty());

        MockMultipartFile file = csv(
                "Ad,Soyad,FIN,Əsas ərazi,Departament,Qrafik\n"
                        + "Leyla,Əliyeva,7ABC123,Baş ofis,İnsan Resursları,Standart qrafik\n");

        DataImportResultDTO result = service.importData("employees", file);

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.getImportedRows()).isEqualTo(1);
        assertThat(result.isDeviceSyncDeferred()).isTrue();
        ArgumentCaptor<EmployeeDTO> dtoCaptor = ArgumentCaptor.forClass(EmployeeDTO.class);
        verify(employeeService).createForImport(dtoCaptor.capture(), eq(null));
        assertThat(dtoCaptor.getValue().getBranchId()).isEqualTo(1L);
        assertThat(dtoCaptor.getValue().getAreaIds()).containsExactly(1L);
        assertThat(dtoCaptor.getValue().getTimetableId()).isEqualTo(3L);
    }

    @Test
    void employeeValidationIsAtomicWhenOneFinAlreadyExists() {
        Employee existing = new Employee();
        existing.setEmployeeId("EMP0001");
        existing.setFinNumber("OLD123");
        when(branchRepository.findByTenantId(7L)).thenReturn(List.of(branch));
        when(departmentRepository.findByTenantId(7L)).thenReturn(List.of(department));
        when(positionRepository.findByTenantId(7L)).thenReturn(List.of());
        when(timetableRepository.findByTenantId(7L)).thenReturn(List.of(timetable));
        when(employeeRepository.findAllByTenantIdOrderByFirstNameAscLastNameAsc(7L))
                .thenReturn(List.of(existing));

        MockMultipartFile file = csv(
                "Ad,Soyad,FIN,Əsas ərazi,Departament,Qrafik\n"
                        + "Yeni,Bir,NEW123,Baş ofis,İnsan Resursları,Standart qrafik\n"
                        + "Köhnə,İki,old123,Baş ofis,İnsan Resursları,Standart qrafik\n");

        DataImportResultDTO result = service.importData("employees", file);

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.getImportedRows()).isZero();
        assertThat(result.getRejectedRows()).isEqualTo(2);
        assertThat(result.getErrors()).anyMatch(error -> error.getField().equals("FIN"));
        verify(employeeService, never()).createForImport(any(), any());
    }

    @Test
    void departmentImportDoesNotSaveAnyRowWhenFileContainsDuplicate() {
        when(branchRepository.findByTenantId(7L)).thenReturn(List.of(branch));
        when(departmentRepository.findByTenantId(7L)).thenReturn(List.of(department));
        MockMultipartFile file = csv(
                "Departament adı,Təsvir,Ərazi\n"
                        + "Maliyyə,Yeni,Baş ofis\n"
                        + "İnsan Resursları,Mövcud,Baş ofis\n");

        DataImportResultDTO result = service.importData("departments", file);

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.getImportedRows()).isZero();
        verify(departmentRepository, never()).save(any());
    }

    @Test
    void importsPositionForResolvedAreaAndDepartment() {
        when(branchRepository.findByTenantId(7L)).thenReturn(List.of(branch));
        when(departmentRepository.findByTenantId(7L)).thenReturn(List.of(department));
        when(positionRepository.findByTenantId(7L)).thenReturn(List.of());
        when(positionRepository.save(any(Position.class))).thenAnswer(invocation -> invocation.getArgument(0));
        MockMultipartFile file = csv(
                "Vəzifə adı,Təsvir,Ərazi,Departament\n"
                        + "HR mütəxəssisi,İşə qəbul,Baş ofis,İnsan Resursları\n");

        DataImportResultDTO result = service.importData("positions", file);

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.getImportedRows()).isEqualTo(1);
        ArgumentCaptor<Position> captor = ArgumentCaptor.forClass(Position.class);
        verify(positionRepository).save(captor.capture());
        assertThat(captor.getValue().getDepartmentId()).isEqualTo(2L);
        assertThat(captor.getValue().getTenantId()).isEqualTo(7L);
    }

    private MockMultipartFile csv(String content) {
        return new MockMultipartFile(
                "file", "import.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }
}
