package com.hic.service;

import com.hic.dto.DataImportErrorDTO;
import com.hic.dto.DataImportResultDTO;
import com.hic.dto.EmployeeDTO;
import com.hic.exception.BadRequestException;
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
import com.hic.util.AppTimeZone;
import com.hic.util.TabularFileUtil;
import com.hic.util.TabularFileUtil.ParsedRow;
import com.hic.util.TabularFileUtil.ParsedTable;
import com.hic.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MasterDataTransferService {

    private static final List<String> DEPARTMENT_HEADERS = List.of(
            "Departament adı", "Təsvir", "Ərazi", "Valideyn departament");
    private static final List<String> POSITION_HEADERS = List.of(
            "Vəzifə adı", "Təsvir", "Ərazi", "Departament");
    private static final List<String> EMPLOYEE_HEADERS = List.of(
            "Əməkdaş ID", "Ad", "Soyad", "Ata adı", "FIN", "Cins", "Telefon", "E-poçt",
            "Doğum tarixi", "Seriya nömrəsi", "Müqavilə nömrəsi", "Əsas ərazi",
            "Əlavə ərazilər", "Departament", "Vəzifə", "Qrafik", "İşə qəbul tarixi",
            "Müqavilə bitmə tarixi", "Status", "Növbə növü", "Ünvan", "Təcili əlaqə", "Qeyd");

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd.MM.uuuu"),
            DateTimeFormatter.ofPattern("dd/MM/uuuu"));

    private final TabularFileUtil tabularFileUtil;
    private final BranchRepository branchRepository;
    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;
    private final EmployeeRepository employeeRepository;
    private final TimetableRepository timetableRepository;
    private final TenantRepository tenantRepository;
    private final EmployeeService employeeService;
    private final EmployeeAreaAssignmentService employeeAreaAssignmentService;
    private final UserScopeService userScopeService;

    @Transactional(readOnly = true)
    public DataFile exportData(String entityValue, String formatValue, boolean template) {
        DataEntity entity = DataEntity.from(entityValue);
        FileFormat format = FileFormat.from(formatValue);
        Long tenantId = requireTenant();
        List<String> headers = headers(entity);
        List<List<String>> rows = template ? List.of() : exportRows(entity, tenantId);
        byte[] content = format == FileFormat.XLSX
                ? template ? exportExcelTemplate(entity, tenantId, headers)
                : tabularFileUtil.writeExcel(entity.sheetName, headers, rows)
                : tabularFileUtil.writeCsv(headers, rows);
        String suffix = template ? "_template" : "";
        return new DataFile(
                content,
                entity.path + suffix + "." + format.extension,
                format.contentType);
    }

    private byte[] exportExcelTemplate(DataEntity entity, Long tenantId, List<String> headers) {
        Long branchScope = userScopeService.resolveBranchScope(null);
        Map<Long, Branch> branches = branchRepository.findByTenantId(tenantId).stream()
                .filter(branch -> branchScope == null || branchScope.equals(branch.getId()))
                .collect(Collectors.toMap(Branch::getId, Function.identity()));
        Map<Long, Department> departments = departmentRepository.findByTenantId(tenantId).stream()
                .filter(department -> branches.containsKey(department.getBranchId()))
                .collect(Collectors.toMap(Department::getId, Function.identity()));
        List<Position> positions = entity == DataEntity.EMPLOYEES
                ? positionRepository.findByTenantId(tenantId).stream()
                    .filter(position -> departments.containsKey(position.getDepartmentId())).toList()
                : List.of();
        Map<String, List<String>> choices = new LinkedHashMap<>();
        choices.put(entity == DataEntity.EMPLOYEES ? "Əsas ərazi" : "Ərazi",
                choiceNames(branches.values().stream().map(Branch::getName).toList()));
        choices.put(entity == DataEntity.DEPARTMENTS ? "Valideyn departament" : "Departament",
                choiceNames(departments.values().stream().map(Department::getDepartmentName).toList()));
        if (entity == DataEntity.EMPLOYEES) {
            choices.put("Vəzifə", choiceNames(positions.stream().map(Position::getPositionName).toList()));
            choices.put("Qrafik", choiceNames(timetableRepository.findByTenantId(tenantId).stream()
                    .map(Timetable::getName).toList()));
        }
        List<List<String>> references = new ArrayList<>();
        references.add(List.of("Ərazi", "Departament", "Vəzifə"));
        departments.values().stream()
                .sorted(Comparator.comparing(Department::getDepartmentName, String.CASE_INSENSITIVE_ORDER))
                .forEach(department -> {
                    List<Position> departmentPositions = positions.stream()
                            .filter(position -> department.getId().equals(position.getDepartmentId()))
                            .sorted(Comparator.comparing(Position::getPositionName, String.CASE_INSENSITIVE_ORDER))
                            .toList();
                    String area = branchName(branches, department.getBranchId());
                    if (departmentPositions.isEmpty()) {
                        references.add(List.of(area, department.getDepartmentName(), ""));
                    } else {
                        departmentPositions.forEach(position -> references.add(
                                List.of(area, department.getDepartmentName(), position.getPositionName())));
                    }
                });
        Set<String> requiredHeaders = switch (entity) {
            case DEPARTMENTS -> Set.of("Departament adı", "Ərazi");
            case POSITIONS -> Set.of("Vəzifə adı", "Ərazi", "Departament");
            case EMPLOYEES -> Set.of("Ad", "Soyad", "FIN", "Əsas ərazi", "Departament", "Qrafik");
        };
        return tabularFileUtil.writeExcelTemplate(entity.sheetName, headers, requiredHeaders, choices, references);
    }

    private List<String> choiceNames(List<String> names) {
        return names.stream().filter(java.util.Objects::nonNull).filter(name -> !name.isBlank())
                .distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    @Transactional
    public DataImportResultDTO importData(String entityValue, MultipartFile file) {
        DataEntity entity = DataEntity.from(entityValue);
        ParsedTable table = tabularFileUtil.read(file);
        return switch (entity) {
            case DEPARTMENTS -> importDepartments(table);
            case POSITIONS -> importPositions(table);
            case EMPLOYEES -> importEmployees(table);
        };
    }

    private List<List<String>> exportRows(DataEntity entity, Long tenantId) {
        return switch (entity) {
            case DEPARTMENTS -> exportDepartments(tenantId);
            case POSITIONS -> exportPositions(tenantId);
            case EMPLOYEES -> exportEmployees(tenantId);
        };
    }

    private List<List<String>> exportDepartments(Long tenantId) {
        Long branchScope = userScopeService.resolveBranchScope(null);
        Map<Long, Branch> branches = branchRepository.findByTenantId(tenantId).stream()
                .collect(Collectors.toMap(Branch::getId, Function.identity()));
        Map<Long, Department> departmentsById = departmentRepository.findByTenantId(tenantId).stream()
                .collect(Collectors.toMap(Department::getId, Function.identity()));
        return departmentsById.values().stream()
                .filter(department -> branchScope == null || branchScope.equals(department.getBranchId()))
                .sorted(Comparator.comparing(Department::getDepartmentName, String.CASE_INSENSITIVE_ORDER))
                .map(department -> List.of(
                        value(department.getDepartmentName()),
                        value(department.getDescription()),
                        branchName(branches, department.getBranchId()),
                        department.getParentDepartmentId() != null
                                ? value(departmentsById.get(department.getParentDepartmentId()) != null
                                ? departmentsById.get(department.getParentDepartmentId()).getDepartmentName()
                                : null)
                                : ""))
                .toList();
    }

    private List<List<String>> exportPositions(Long tenantId) {
        Long branchScope = userScopeService.resolveBranchScope(null);
        Map<Long, Branch> branches = branchRepository.findByTenantId(tenantId).stream()
                .collect(Collectors.toMap(Branch::getId, Function.identity()));
        Map<Long, Department> departments = departmentRepository.findByTenantId(tenantId).stream()
                .collect(Collectors.toMap(Department::getId, Function.identity()));
        return positionRepository.findByTenantId(tenantId).stream()
                .filter(position -> {
                    Department department = departments.get(position.getDepartmentId());
                    return department != null
                            && (branchScope == null || branchScope.equals(department.getBranchId()));
                })
                .sorted(Comparator.comparing(Position::getPositionName, String.CASE_INSENSITIVE_ORDER))
                .map(position -> {
                    Department department = departments.get(position.getDepartmentId());
                    return List.of(
                            value(position.getPositionName()),
                            value(position.getDescription()),
                            branchName(branches, department.getBranchId()),
                            value(department.getDepartmentName()));
                })
                .toList();
    }

    private List<List<String>> exportEmployees(Long tenantId) {
        Long branchScope = userScopeService.resolveBranchScope(null);
        List<Employee> employees = employeeRepository.findAllByTenantIdOrderByFirstNameAscLastNameAsc(tenantId);
        EmployeeAreaAssignmentService.AreaBatch areaBatch = employeeAreaAssignmentService.loadAreaBatch(
                employees.stream().map(Employee::getId).toList());
        Map<Long, Branch> branches = branchRepository.findByTenantId(tenantId).stream()
                .collect(Collectors.toMap(Branch::getId, Function.identity()));
        Map<Long, String> departmentNames = departmentRepository.findByTenantId(tenantId).stream()
                .collect(Collectors.toMap(Department::getId, Department::getDepartmentName));
        Map<Long, String> positionNames = positionRepository.findByTenantId(tenantId).stream()
                .collect(Collectors.toMap(Position::getId, Position::getPositionName));
        Map<Long, String> timetableNames = timetableRepository.findByTenantId(tenantId).stream()
                .collect(Collectors.toMap(Timetable::getId, Timetable::getName));

        return employees.stream()
                .filter(employee -> isEmployeeInScope(employee, branchScope, areaBatch))
                .map(employee -> {
                    List<Long> areaIds = areaBatch.areaIdsByEmployee().getOrDefault(employee.getId(), List.of());
                    if (areaIds.isEmpty() && employee.getBranchId() != null) {
                        areaIds = List.of(employee.getBranchId());
                    }
                    String primaryArea = areaIds.isEmpty() ? "" : branchName(branches, areaIds.get(0));
                    String additionalAreas = areaIds.stream().skip(1)
                            .map(areaId -> branchName(branches, areaId))
                            .filter(name -> !name.isBlank())
                            .collect(Collectors.joining("; "));
                    return List.of(
                            value(employee.getEmployeeId()),
                            value(employee.getFirstName()),
                            value(employee.getLastName()),
                            value(employee.getFatherName()),
                            value(employee.getFinNumber()),
                            value(employee.getGender()),
                            value(employee.getMobilePhone()),
                            value(employee.getEmail()),
                            value(employee.getBirthDate()),
                            value(employee.getSerialNumber()),
                            value(employee.getContractNumber()),
                            primaryArea,
                            additionalAreas,
                            value(departmentNames.get(employee.getDepartmentId())),
                            value(positionNames.get(employee.getPositionId())),
                            value(timetableNames.get(employee.getTimetableId())),
                            value(employee.getHireDate()),
                            value(employee.getContractEndDate()),
                            employee.getEmploymentStatus() != null ? employee.getEmploymentStatus().name() : "ACTIVE",
                            value(employee.getShiftType()),
                            value(employee.getAddress()),
                            value(employee.getEmergencyContact()),
                            value(employee.getNotes()));
                })
                .toList();
    }

    private DataImportResultDTO importDepartments(ParsedTable table) {
        DataImportResultDTO result = newResult(DataEntity.DEPARTMENTS, table.rows().size(), false);
        requireHeaders(table, List.of("Departament adı", "Ərazi"), result);
        if (!result.isSuccessful()) {
            return reject(result);
        }

        Long tenantId = requireTenant();
        Long branchScope = userScopeService.resolveBranchScope(null);
        Map<String, List<Branch>> branches = branchLookup(branchRepository.findByTenantId(tenantId));
        List<Department> existingDepartments = departmentRepository.findByTenantId(tenantId);
        Map<String, List<Department>> existingByKey = groupByKey(
                existingDepartments,
                department -> departmentKey(department.getBranchId(), department.getDepartmentName()));
        Set<String> fileKeys = new LinkedHashSet<>();
        List<DepartmentImportRow> rows = new ArrayList<>();

        for (ParsedRow row : table.rows()) {
            String name = required(row, "Departament adı", result);
            String description = row.get("Təsvir");
            String parentName = row.get("Valideyn departament").trim();
            validateLength(row.rowNumber(), "Departament adı", name, 255, result);
            validateLength(row.rowNumber(), "Təsvir", description, 255, result);
            validateLength(row.rowNumber(), "Valideyn departament", parentName, 255, result);
            Branch branch = resolveBranch(row.get("Ərazi"), row.rowNumber(), branchScope, branches, result);
            if (name.isBlank() || branch == null) {
                continue;
            }
            String key = departmentKey(branch.getId(), name);
            if (!fileKeys.add(key)) {
                addError(result, row.rowNumber(), "Departament adı", "Faylda eyni ərazi üzrə təkrarlanır");
            }
            if (existingByKey.containsKey(key)) {
                addError(result, row.rowNumber(), "Departament adı", "Bu departament həmin ərazidə artıq mövcuddur");
            }
            rows.add(new DepartmentImportRow(
                    row.rowNumber(), name.trim(), description, branch, parentName, key));
        }

        for (DepartmentImportRow row : rows) {
            if (row.parentName().isBlank()) {
                continue;
            }
            String parentKey = departmentKey(row.branch().getId(), row.parentName());
            if (parentKey.equals(row.key())) {
                addError(result, row.rowNumber(), "Valideyn departament", "Departament özü-özünün valideyni ola bilməz");
            } else if (!fileKeys.contains(parentKey) && !existingByKey.containsKey(parentKey)) {
                addError(result, row.rowNumber(), "Valideyn departament", "Həmin ərazidə tapılmadı");
            } else if (existingByKey.getOrDefault(parentKey, List.of()).size() > 1) {
                addError(result, row.rowNumber(), "Valideyn departament", "Eyni adlı bir neçə departament tapıldı");
            }
        }

        List<DepartmentImportRow> orderedRows = orderDepartments(rows, existingByKey.keySet(), result);
        if (!result.isSuccessful()) {
            return reject(result);
        }

        Map<String, Department> created = new LinkedHashMap<>();
        for (DepartmentImportRow row : orderedRows) {
            Department department = new Department();
            department.setTenantId(tenantId);
            department.setDepartmentName(row.name());
            department.setDescription(blankToNull(row.description()));
            department.setBranchId(row.branch().getId());
            department.setCalculateOvertime(false);
            department.setFlexShift(false);
            if (!row.parentName().isBlank()) {
                String parentKey = departmentKey(row.branch().getId(), row.parentName());
                Department parent = created.get(parentKey);
                if (parent == null) {
                    parent = existingByKey.get(parentKey).get(0);
                }
                department.setParentDepartmentId(parent.getId());
            }
            created.put(row.key(), departmentRepository.save(department));
        }
        return imported(result, created.size());
    }

    private DataImportResultDTO importPositions(ParsedTable table) {
        DataImportResultDTO result = newResult(DataEntity.POSITIONS, table.rows().size(), false);
        requireHeaders(table, List.of("Vəzifə adı", "Ərazi", "Departament"), result);
        if (!result.isSuccessful()) {
            return reject(result);
        }

        Long tenantId = requireTenant();
        Long branchScope = userScopeService.resolveBranchScope(null);
        Map<String, List<Branch>> branches = branchLookup(branchRepository.findByTenantId(tenantId));
        Map<String, List<Department>> departments = groupByKey(
                departmentRepository.findByTenantId(tenantId),
                department -> departmentKey(department.getBranchId(), department.getDepartmentName()));
        Map<String, List<Position>> existingPositions = groupByKey(
                positionRepository.findByTenantId(tenantId),
                position -> positionKey(position.getDepartmentId(), position.getPositionName()));
        Set<String> fileKeys = new LinkedHashSet<>();
        List<PositionImportRow> rows = new ArrayList<>();

        for (ParsedRow row : table.rows()) {
            String name = required(row, "Vəzifə adı", result);
            String description = row.get("Təsvir");
            validateLength(row.rowNumber(), "Vəzifə adı", name, 255, result);
            validateLength(row.rowNumber(), "Təsvir", description, 255, result);
            Branch branch = resolveBranch(row.get("Ərazi"), row.rowNumber(), branchScope, branches, result);
            Department department = branch != null
                    ? resolveDepartment(row.get("Departament"), branch, row.rowNumber(), departments, result)
                    : null;
            if (name.isBlank() || department == null) {
                continue;
            }
            String key = positionKey(department.getId(), name);
            if (!fileKeys.add(key)) {
                addError(result, row.rowNumber(), "Vəzifə adı", "Faylda eyni departament üzrə təkrarlanır");
            }
            if (existingPositions.containsKey(key)) {
                addError(result, row.rowNumber(), "Vəzifə adı", "Bu vəzifə departamentdə artıq mövcuddur");
            }
            rows.add(new PositionImportRow(name.trim(), description, department));
        }
        if (!result.isSuccessful()) {
            return reject(result);
        }

        for (PositionImportRow row : rows) {
            Position position = new Position();
            position.setTenantId(tenantId);
            position.setPositionName(row.name());
            position.setDescription(blankToNull(row.description()));
            position.setDepartmentId(row.department().getId());
            positionRepository.save(position);
        }
        return imported(result, rows.size());
    }

    private DataImportResultDTO importEmployees(ParsedTable table) {
        DataImportResultDTO result = newResult(DataEntity.EMPLOYEES, table.rows().size(), true);
        requireHeaders(table, List.of(
                "Ad", "Soyad", "FIN", "Əsas ərazi", "Departament", "Qrafik"), result);
        if (!result.isSuccessful()) {
            return reject(result);
        }

        Long tenantId = requireTenant();
        Long branchScope = userScopeService.resolveBranchScope(null);
        Map<String, List<Branch>> branches = branchLookup(branchRepository.findByTenantId(tenantId));
        Map<String, List<Department>> departments = groupByKey(
                departmentRepository.findByTenantId(tenantId),
                department -> departmentKey(department.getBranchId(), department.getDepartmentName()));
        Map<String, List<Position>> positions = groupByKey(
                positionRepository.findByTenantId(tenantId),
                position -> positionKey(position.getDepartmentId(), position.getPositionName()));
        Map<String, List<Timetable>> timetables = groupByKey(
                timetableRepository.findByTenantId(tenantId),
                timetable -> normalize(timetable.getName()));
        List<Employee> existingEmployees = employeeRepository.findAllByTenantIdOrderByFirstNameAscLastNameAsc(tenantId);
        Set<String> existingEmployeeIds = existingEmployees.stream()
                .map(Employee::getEmployeeId).filter(java.util.Objects::nonNull)
                .map(MasterDataTransferService::normalize).collect(Collectors.toSet());
        Set<String> existingFins = existingEmployees.stream()
                .map(Employee::getFinNumber).filter(java.util.Objects::nonNull)
                .map(MasterDataTransferService::normalize).collect(Collectors.toSet());
        Set<String> fileEmployeeIds = new LinkedHashSet<>();
        Set<String> fileFins = new LinkedHashSet<>();
        List<EmployeeImportRow> rows = new ArrayList<>();

        for (ParsedRow row : table.rows()) {
            String employeeId = row.get("Əməkdaş ID").trim();
            String firstName = required(row, "Ad", result);
            String lastName = required(row, "Soyad", result);
            String fin = required(row, "FIN", result);
            validateLength(row.rowNumber(), "Əməkdaş ID", employeeId, 255, result);
            validateLength(row.rowNumber(), "Ad", firstName, 255, result);
            validateLength(row.rowNumber(), "Soyad", lastName, 255, result);
            validateLength(row.rowNumber(), "FIN", fin, 255, result);
            validateLength(row.rowNumber(), "Ata adı", row.get("Ata adı"), 255, result);
            validateLength(row.rowNumber(), "Cins", row.get("Cins"), 255, result);
            validateLength(row.rowNumber(), "Telefon", row.get("Telefon"), 255, result);
            validateLength(row.rowNumber(), "E-poçt", row.get("E-poçt"), 255, result);
            validateLength(row.rowNumber(), "Seriya nömrəsi", row.get("Seriya nömrəsi"), 255, result);
            validateLength(row.rowNumber(), "Müqavilə nömrəsi", row.get("Müqavilə nömrəsi"), 255, result);
            validateLength(row.rowNumber(), "Növbə növü", row.get("Növbə növü"), 255, result);
            validateLength(row.rowNumber(), "Ünvan", row.get("Ünvan"), 255, result);
            validateLength(row.rowNumber(), "Təcili əlaqə", row.get("Təcili əlaqə"), 255, result);

            if (!employeeId.isBlank()) {
                String normalizedId = normalize(employeeId);
                if (existingEmployeeIds.contains(normalizedId)) {
                    addError(result, row.rowNumber(), "Əməkdaş ID", "Artıq mövcuddur");
                }
                if (!fileEmployeeIds.add(normalizedId)) {
                    addError(result, row.rowNumber(), "Əməkdaş ID", "Faylda təkrarlanır");
                }
            }
            if (!fin.isBlank()) {
                String normalizedFin = normalize(fin);
                if (existingFins.contains(normalizedFin)) {
                    addError(result, row.rowNumber(), "FIN", "Bu FIN kodu artıq mövcuddur");
                }
                if (!fileFins.add(normalizedFin)) {
                    addError(result, row.rowNumber(), "FIN", "Faylda təkrarlanır");
                }
            }

            Branch primaryBranch = resolveBranch(
                    row.get("Əsas ərazi"), row.rowNumber(), branchScope, branches, result);
            LinkedHashSet<Long> areaIds = new LinkedHashSet<>();
            if (primaryBranch != null) {
                areaIds.add(primaryBranch.getId());
            }
            for (String areaName : splitAreas(row.get("Əlavə ərazilər"))) {
                Branch additionalBranch = resolveBranch(
                        areaName, row.rowNumber(), branchScope, branches, result);
                if (additionalBranch != null) {
                    areaIds.add(additionalBranch.getId());
                }
            }

            Department department = primaryBranch != null
                    ? resolveDepartment(row.get("Departament"), primaryBranch, row.rowNumber(), departments, result)
                    : null;
            Position position = department != null && !row.get("Vəzifə").isBlank()
                    ? resolvePosition(row.get("Vəzifə"), department, row.rowNumber(), positions, result)
                    : null;
            Timetable timetable = resolveTimetable(
                    row.get("Qrafik"), row.rowNumber(), timetables, result);
            Employee.EmploymentStatus status = parseStatus(row.get("Status"), row.rowNumber(), result);
            LocalDate birthDate = parseDate(row.get("Doğum tarixi"), row.rowNumber(), "Doğum tarixi", result);
            LocalDate hireDate = parseDate(row.get("İşə qəbul tarixi"), row.rowNumber(), "İşə qəbul tarixi", result);
            LocalDate contractEndDate = parseDate(
                    row.get("Müqavilə bitmə tarixi"), row.rowNumber(), "Müqavilə bitmə tarixi", result);
            String email = row.get("E-poçt").trim();
            if (!email.isBlank() && !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
                addError(result, row.rowNumber(), "E-poçt", "Düzgün e-poçt ünvanı deyil");
            }

            if (firstName.isBlank() || lastName.isBlank() || fin.isBlank()
                    || primaryBranch == null || department == null || timetable == null) {
                continue;
            }
            EmployeeDTO dto = new EmployeeDTO();
            dto.setFirstName(firstName.trim());
            dto.setLastName(lastName.trim());
            dto.setFatherName(blankToNull(row.get("Ata adı")));
            dto.setFinNumber(fin.trim());
            dto.setGender(blankToNull(row.get("Cins")));
            dto.setMobilePhone(blankToNull(row.get("Telefon")));
            dto.setEmail(blankToNull(email));
            dto.setBirthDate(birthDate);
            dto.setSerialNumber(blankToNull(row.get("Seriya nömrəsi")));
            dto.setContractNumber(blankToNull(row.get("Müqavilə nömrəsi")));
            dto.setBranchId(primaryBranch.getId());
            dto.setAreaIds(List.copyOf(areaIds));
            dto.setDepartmentId(department.getId());
            dto.setPositionId(position != null ? position.getId() : null);
            dto.setTimetableId(timetable.getId());
            dto.setHireDate(hireDate != null ? hireDate : AppTimeZone.today());
            dto.setContractEndDate(contractEndDate);
            dto.setEmploymentStatus(status != null ? status : Employee.EmploymentStatus.ACTIVE);
            dto.setShiftType(!row.get("Növbə növü").isBlank()
                    ? row.get("Növbə növü").trim()
                    : timetable.getShiftType());
            dto.setAddress(blankToNull(row.get("Ünvan")));
            dto.setEmergencyContact(blankToNull(row.get("Təcili əlaqə")));
            dto.setNotes(blankToNull(row.get("Qeyd")));
            rows.add(new EmployeeImportRow(blankToNull(employeeId), dto));
        }

        if (result.isSuccessful()) {
            tenantRepository.findById(tenantId).ifPresent(tenant -> {
                Integer maximum = tenant.getMaxEmployees();
                long current = employeeRepository.countByTenantId(tenantId);
                if (maximum != null && maximum > 0 && current + rows.size() > maximum) {
                    addError(result, 0, "Fayl",
                            "Əməkdaş limiti aşılır. Mövcud: " + current
                                    + ", import: " + rows.size() + ", limit: " + maximum);
                }
            });
        }
        if (!result.isSuccessful()) {
            return reject(result);
        }

        for (EmployeeImportRow row : rows) {
            employeeService.createForImport(row.dto(), row.employeeId());
        }
        return imported(result, rows.size());
    }

    private List<DepartmentImportRow> orderDepartments(
            List<DepartmentImportRow> rows,
            Set<String> existingKeys,
            DataImportResultDTO result) {
        List<DepartmentImportRow> pending = new ArrayList<>(rows);
        List<DepartmentImportRow> ordered = new ArrayList<>();
        Set<String> resolved = new LinkedHashSet<>(existingKeys);
        while (!pending.isEmpty()) {
            List<DepartmentImportRow> ready = pending.stream()
                    .filter(row -> row.parentName().isBlank()
                            || resolved.contains(departmentKey(row.branch().getId(), row.parentName())))
                    .toList();
            if (ready.isEmpty()) {
                pending.forEach(row -> addError(
                        result, row.rowNumber(), "Valideyn departament", "Valideyn əlaqəsində dövrə aşkarlandı"));
                return List.of();
            }
            ordered.addAll(ready);
            ready.forEach(row -> resolved.add(row.key()));
            pending.removeAll(ready);
        }
        return ordered;
    }

    private Branch resolveBranch(
            String value,
            int row,
            Long branchScope,
            Map<String, List<Branch>> lookup,
            DataImportResultDTO result) {
        if (value == null || value.isBlank()) {
            addError(result, row, "Ərazi", "Mütləq doldurulmalıdır");
            return null;
        }
        List<Branch> matches = lookup.getOrDefault(normalize(value), List.of());
        if (matches.isEmpty()) {
            addError(result, row, "Ərazi", "Ad və ya kod üzrə tapılmadı: " + value);
            return null;
        }
        if (matches.size() > 1) {
            addError(result, row, "Ərazi", "Eyni ad/kod üzrə bir neçə ərazi tapıldı: " + value);
            return null;
        }
        Branch branch = matches.get(0);
        if (branchScope != null && !branchScope.equals(branch.getId())) {
            addError(result, row, "Ərazi", "Bu ərazi üçün icazəniz yoxdur");
            return null;
        }
        return branch;
    }

    private Department resolveDepartment(
            String value,
            Branch branch,
            int row,
            Map<String, List<Department>> lookup,
            DataImportResultDTO result) {
        if (value == null || value.isBlank()) {
            addError(result, row, "Departament", "Mütləq doldurulmalıdır");
            return null;
        }
        List<Department> matches = lookup.getOrDefault(departmentKey(branch.getId(), value), List.of());
        if (matches.isEmpty()) {
            addError(result, row, "Departament", "Seçilən ərazidə tapılmadı: " + value);
            return null;
        }
        if (matches.size() > 1) {
            addError(result, row, "Departament", "Seçilən ərazidə eyni adlı bir neçə departament var");
            return null;
        }
        return matches.get(0);
    }

    private Position resolvePosition(
            String value,
            Department department,
            int row,
            Map<String, List<Position>> lookup,
            DataImportResultDTO result) {
        List<Position> matches = lookup.getOrDefault(positionKey(department.getId(), value), List.of());
        if (matches.isEmpty()) {
            addError(result, row, "Vəzifə", "Seçilən departamentdə tapılmadı: " + value);
            return null;
        }
        if (matches.size() > 1) {
            addError(result, row, "Vəzifə", "Seçilən departamentdə eyni adlı bir neçə vəzifə var");
            return null;
        }
        return matches.get(0);
    }

    private Timetable resolveTimetable(
            String value,
            int row,
            Map<String, List<Timetable>> lookup,
            DataImportResultDTO result) {
        if (value == null || value.isBlank()) {
            addError(result, row, "Qrafik", "Mütləq doldurulmalıdır");
            return null;
        }
        List<Timetable> matches = lookup.getOrDefault(normalize(value), List.of());
        if (matches.isEmpty()) {
            addError(result, row, "Qrafik", "Tapılmadı: " + value);
            return null;
        }
        if (matches.size() > 1) {
            addError(result, row, "Qrafik", "Eyni adlı bir neçə qrafik var");
            return null;
        }
        return matches.get(0);
    }

    private Employee.EmploymentStatus parseStatus(String value, int row, DataImportResultDTO result) {
        if (value == null || value.isBlank()) {
            return Employee.EmploymentStatus.ACTIVE;
        }
        return switch (normalize(value)) {
            case "active", "aktiv" -> Employee.EmploymentStatus.ACTIVE;
            case "inactive", "deaktiv" -> Employee.EmploymentStatus.INACTIVE;
            case "on_leave", "on leave", "məzuniyyətdə", "mezuniyyetde" -> Employee.EmploymentStatus.ON_LEAVE;
            default -> {
                addError(result, row, "Status", "ACTIVE, INACTIVE və ya ON_LEAVE olmalıdır");
                yield null;
            }
        };
    }

    private LocalDate parseDate(String value, int row, String field, DataImportResultDTO result) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(value.trim(), formatter);
            } catch (DateTimeParseException ignored) {
                // Try the next supported format.
            }
        }
        addError(result, row, field, "Tarix YYYY-MM-DD və ya DD.MM.YYYY formatında olmalıdır");
        return null;
    }

    private List<String> splitAreas(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(value.split(";"))
                .map(String::trim)
                .filter(part -> !part.isBlank())
                .toList();
    }

    private Map<String, List<Branch>> branchLookup(List<Branch> branches) {
        Map<String, LinkedHashSet<Branch>> grouped = new LinkedHashMap<>();
        for (Branch branch : branches) {
            addLookupValue(grouped, branch.getName(), branch);
            addLookupValue(grouped, branch.getCode(), branch);
        }
        Map<String, List<Branch>> result = new LinkedHashMap<>();
        grouped.forEach((key, values) -> result.put(key, List.copyOf(values)));
        return result;
    }

    private <T> void addLookupValue(Map<String, LinkedHashSet<T>> lookup, String key, T value) {
        if (key == null || key.isBlank()) {
            return;
        }
        lookup.computeIfAbsent(normalize(key), ignored -> new LinkedHashSet<>()).add(value);
    }

    private <T> Map<String, List<T>> groupByKey(Collection<T> values, Function<T, String> keyFunction) {
        return values.stream().collect(Collectors.groupingBy(
                keyFunction,
                LinkedHashMap::new,
                Collectors.toList()));
    }

    private void requireHeaders(ParsedTable table, List<String> required, DataImportResultDTO result) {
        for (String header : required) {
            if (!table.hasHeader(header)) {
                addError(result, 1, header, "Başlıq faylda yoxdur");
            }
        }
        if (table.rows().isEmpty()) {
            addError(result, 1, "Fayl", "Import ediləcək məlumat sətri yoxdur");
        }
    }

    private String required(ParsedRow row, String field, DataImportResultDTO result) {
        String value = row.get(field).trim();
        if (value.isBlank()) {
            addError(result, row.rowNumber(), field, "Mütləq doldurulmalıdır");
        }
        return value;
    }

    private void validateLength(
            int row,
            String field,
            String value,
            int maximum,
            DataImportResultDTO result) {
        if (value != null && value.length() > maximum) {
            addError(result, row, field, "Maksimum " + maximum + " simvol ola bilər");
        }
    }

    private DataImportResultDTO newResult(DataEntity entity, int totalRows, boolean deviceSyncDeferred) {
        return DataImportResultDTO.builder()
                .entity(entity.path)
                .totalRows(totalRows)
                .importedRows(0)
                .rejectedRows(0)
                .deviceSyncDeferred(deviceSyncDeferred)
                .errors(new ArrayList<>())
                .build();
    }

    private DataImportResultDTO reject(DataImportResultDTO result) {
        result.setImportedRows(0);
        result.setRejectedRows(result.getTotalRows());
        return result;
    }

    private DataImportResultDTO imported(DataImportResultDTO result, int importedRows) {
        result.setImportedRows(importedRows);
        result.setRejectedRows(0);
        return result;
    }

    private void addError(DataImportResultDTO result, int row, String field, String message) {
        result.getErrors().add(new DataImportErrorDTO(row, field, message));
    }

    private boolean isEmployeeInScope(
            Employee employee,
            Long branchScope,
            EmployeeAreaAssignmentService.AreaBatch areaBatch) {
        if (branchScope == null) {
            return true;
        }
        List<Long> areaIds = areaBatch.areaIdsByEmployee().getOrDefault(employee.getId(), List.of());
        return areaIds.contains(branchScope) || (areaIds.isEmpty() && branchScope.equals(employee.getBranchId()));
    }

    private String branchName(Map<Long, Branch> branches, Long branchId) {
        Branch branch = branchId != null ? branches.get(branchId) : null;
        return branch != null ? value(branch.getName()) : "";
    }

    private List<String> headers(DataEntity entity) {
        return switch (entity) {
            case DEPARTMENTS -> DEPARTMENT_HEADERS;
            case POSITIONS -> POSITION_HEADERS;
            case EMPLOYEES -> EMPLOYEE_HEADERS;
        };
    }

    private Long requireTenant() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BadRequestException("Tenant context is required");
        }
        return tenantId;
    }

    private static String departmentKey(Long branchId, String name) {
        return branchId + "|" + normalize(name);
    }

    private static String positionKey(Long departmentId, String name) {
        return departmentId + "|" + normalize(name);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String value(Object value) {
        return value != null ? String.valueOf(value) : "";
    }

    public record DataFile(byte[] content, String filename, String contentType) {
    }

    private record DepartmentImportRow(
            int rowNumber,
            String name,
            String description,
            Branch branch,
            String parentName,
            String key) {
    }

    private record PositionImportRow(String name, String description, Department department) {
    }

    private record EmployeeImportRow(String employeeId, EmployeeDTO dto) {
    }

    private enum DataEntity {
        EMPLOYEES("employees", "Employees"),
        DEPARTMENTS("departments", "Departments"),
        POSITIONS("positions", "Positions");

        private final String path;
        private final String sheetName;

        DataEntity(String path, String sheetName) {
            this.path = path;
            this.sheetName = sheetName;
        }

        private static DataEntity from(String value) {
            return java.util.Arrays.stream(values())
                    .filter(entity -> entity.path.equalsIgnoreCase(value))
                    .findFirst()
                    .orElseThrow(() -> new BadRequestException("Dəstəklənməyən məlumat növü: " + value));
        }
    }

    private enum FileFormat {
        XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
        CSV("csv", "text/csv; charset=UTF-8");

        private final String extension;
        private final String contentType;

        FileFormat(String extension, String contentType) {
            this.extension = extension;
            this.contentType = contentType;
        }

        private static FileFormat from(String value) {
            String normalized = value == null || value.isBlank() ? "xlsx" : value.trim();
            return java.util.Arrays.stream(values())
                    .filter(format -> format.extension.equalsIgnoreCase(normalized))
                    .findFirst()
                    .orElseThrow(() -> new BadRequestException("Format xlsx və ya csv olmalıdır"));
        }
    }
}
