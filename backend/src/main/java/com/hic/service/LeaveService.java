package com.hic.service;

import com.hic.dto.LeaveRequestDTO;
import com.hic.exception.BadRequestException;
import com.hic.exception.ResourceNotFoundException;
import com.hic.model.LeaveRequest;
import com.hic.model.LeaveRequest.LeaveStatus;
import com.hic.model.LeaveType;
import com.hic.repository.EmployeeRepository;
import com.hic.repository.LeaveRequestRepository;
import com.hic.repository.LeaveTypeRepository;
import com.hic.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LeaveService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveTypeRepository leaveTypeRepository;

    public List<LeaveRequestDTO> getAll() {
        Long tenantId = requireTenantId();
        return leaveRequestRepository.findByTenantId(tenantId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<LeaveType> getAllLeaveTypes() {
        Long tenantId = requireTenantId();
        return leaveTypeRepository.findByTenantId(tenantId);
    }

    public List<LeaveRequestDTO> getByEmployee(Long employeeId) {
        Long tenantId = requireTenantId();
        return leaveRequestRepository.findByTenantIdAndEmployeeId(tenantId, employeeId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public LeaveRequestDTO getById(Long id) {
        return toDTO(requireLeaveInTenant(id));
    }

    public boolean hasActiveLeave(Long employeeId, LocalDate date) {
        Long tenantId = TenantContext.getTenantId();
        List<LeaveRequest> requests;
        if (tenantId != null) {
            requests = leaveRequestRepository.findApprovedByTenantAndEmployeeIdAndDateRange(
                    tenantId, employeeId, date, date);
            return !requests.isEmpty();
        }
        requests = leaveRequestRepository.findByEmployeeIdAndStatus(employeeId, LeaveStatus.APPROVED);
        return requests.stream().anyMatch(request ->
                !date.isBefore(request.getStartDate()) && !date.isAfter(request.getEndDate()));
    }

    @Transactional
    public LeaveRequestDTO create(LeaveRequestDTO dto) {
        Long tenantId = requireTenantId();
        if (!employeeRepository.existsById(dto.getEmployeeId())) {
            throw new ResourceNotFoundException("Employee", dto.getEmployeeId());
        }
        if (!leaveTypeRepository.existsById(dto.getLeaveTypeId())) {
            throw new ResourceNotFoundException("LeaveType", dto.getLeaveTypeId());
        }
        if (dto.getEndDate().isBefore(dto.getStartDate())) {
            throw new BadRequestException("End date cannot be before start date");
        }
        LeaveRequest req = new LeaveRequest();
        req.setEmployeeId(dto.getEmployeeId());
        req.setLeaveTypeId(dto.getLeaveTypeId());
        req.setStartDate(dto.getStartDate());
        req.setEndDate(dto.getEndDate());
        req.setStatus(LeaveStatus.PENDING);
        req.setTenantId(tenantId);
        return toDTO(leaveRequestRepository.save(req));
    }

    @Transactional
    public LeaveRequestDTO updateStatus(Long id, LeaveStatus status, Long approvedBy) {
        LeaveRequest req = requireLeaveInTenant(id);
        req.setStatus(status);
        if (status == LeaveStatus.APPROVED) {
            req.setApprovedBy(approvedBy != null ? approvedBy : TenantContext.getUserId());
            req.setApprovalDate(LocalDate.now());
        }
        return toDTO(leaveRequestRepository.save(req));
    }

    @Transactional
    public void delete(Long id) {
        LeaveRequest req = requireLeaveInTenant(id);
        leaveRequestRepository.deleteById(req.getId());
    }

    private LeaveRequest requireLeaveInTenant(Long id) {
        LeaveRequest req = leaveRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("LeaveRequest", id));
        Long tenantId = TenantContext.getTenantId();
        if (tenantId != null && (req.getTenantId() == null || !tenantId.equals(req.getTenantId()))) {
            throw new ResourceNotFoundException("LeaveRequest", id);
        }
        return req;
    }

    private Long requireTenantId() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BadRequestException("Tenant context is required");
        }
        return tenantId;
    }

    private LeaveRequestDTO toDTO(LeaveRequest req) {
        LeaveRequestDTO dto = new LeaveRequestDTO();
        dto.setId(req.getId());
        dto.setEmployeeId(req.getEmployeeId());
        dto.setLeaveTypeId(req.getLeaveTypeId());
        dto.setStartDate(req.getStartDate());
        dto.setEndDate(req.getEndDate());
        dto.setStatus(req.getStatus());
        dto.setApprovedBy(req.getApprovedBy());
        dto.setApprovalDate(req.getApprovalDate());
        dto.setCreatedAt(req.getCreatedAt());
        return dto;
    }
}
