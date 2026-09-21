package com.hic.service;

import com.hic.dto.PositionDTO;
import com.hic.exception.ResourceNotFoundException;
import com.hic.model.Position;
import com.hic.repository.DepartmentRepository;
import com.hic.repository.EmployeeRepository;
import com.hic.repository.PositionRepository;
import com.hic.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PositionService {

    private final PositionRepository positionRepository;
    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;

    public List<PositionDTO> getAll() {
        Long tenantId = TenantContext.getTenantId();
        List<Position> positions = tenantId != null
                ? positionRepository.findByTenantId(tenantId)
                : positionRepository.findAll();
        return positions.stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<PositionDTO> getByDepartment(Long departmentId) {
        Long tenantId = TenantContext.getTenantId();
        List<Position> positions = tenantId != null
                ? positionRepository.findByTenantIdAndDepartmentId(tenantId, departmentId)
                : positionRepository.findByDepartmentId(departmentId);
        return positions.stream().map(this::toDTO).collect(Collectors.toList());
    }

    public PositionDTO getById(Long id) {
        return toDTO(positionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Position", id)));
    }

    @Transactional
    public PositionDTO create(PositionDTO dto) {
        if (!departmentRepository.existsById(dto.getDepartmentId())) {
            throw new ResourceNotFoundException("Department", dto.getDepartmentId());
        }
        Position position = new Position();
        position.setPositionName(dto.getPositionName());
        position.setDescription(dto.getDescription());
        position.setDepartmentId(dto.getDepartmentId());
        Long tenantId = TenantContext.getTenantId();
        if (tenantId != null) position.setTenantId(tenantId);
        return toDTO(positionRepository.save(position));
    }

    @Transactional
    public PositionDTO update(Long id, PositionDTO dto) {
        Position position = positionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Position", id));
        position.setPositionName(dto.getPositionName());
        position.setDescription(dto.getDescription());
        if (dto.getDepartmentId() != null) {
            if (!departmentRepository.existsById(dto.getDepartmentId())) {
                throw new ResourceNotFoundException("Department", dto.getDepartmentId());
            }
            position.setDepartmentId(dto.getDepartmentId());
        }
        return toDTO(positionRepository.save(position));
    }

    @Transactional
    public void delete(Long id) {
        if (!positionRepository.existsById(id)) {
            throw new ResourceNotFoundException("Position", id);
        }
        positionRepository.deleteById(id);
    }

    private PositionDTO toDTO(Position position) {
        PositionDTO dto = new PositionDTO();
        dto.setId(position.getId());
        dto.setPositionName(position.getPositionName());
        dto.setDescription(position.getDescription());
        dto.setDepartmentId(position.getDepartmentId());
        dto.setCreatedAt(position.getCreatedAt());

        if (position.getDepartmentId() != null) {
            departmentRepository.findById(position.getDepartmentId())
                    .ifPresent(d -> dto.setDepartmentName(d.getDepartmentName()));
        }

        dto.setEmployeeCount(employeeRepository.countByPositionId(position.getId()));

        return dto;
    }
}
