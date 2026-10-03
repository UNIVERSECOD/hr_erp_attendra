package com.hic.service;

import com.hic.dto.DeviceEmployeeAssignmentDTO.EmployeeSyncResult;
import com.hic.exception.ResourceNotFoundException;
import com.hic.model.Employee;
import com.hic.repository.EmployeeRepository;
import com.hic.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class EmployeeFaceSynchronizationService {

    private final EmployeeRepository employeeRepository;
    private final EmployeeFaceImageService employeeFaceImageService;
    private final DeviceEmployeeAssignmentService deviceEmployeeAssignmentService;

    public EmployeeSyncResult saveAndSync(Long employeeId, MultipartFile file) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", employeeId));
        Long tenantId = TenantContext.getTenantId();
        if (tenantId != null && !tenantId.equals(employee.getTenantId())) {
            throw new ResourceNotFoundException("Employee", employeeId);
        }

        employeeFaceImageService.saveFaceImage(employeeId, file);
        return deviceEmployeeAssignmentService.syncEmployee(employeeId);
    }
}
