package com.hic.service;

import com.hic.dto.DoorDTO;
import com.hic.exception.BadRequestException;
import com.hic.exception.ResourceNotFoundException;
import com.hic.model.Door;
import com.hic.repository.DeviceConfigRepository;
import com.hic.repository.DoorRepository;
import com.hic.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DoorService {

    private final DeviceConfigRepository deviceConfigRepository;
    private final DoorRepository doorRepository;

    public List<DoorDTO.Response> findByBranch(Long branchId) {
        Long tenantId = TenantContext.getTenantId();
        List<Door> doors = tenantId != null
                ? doorRepository.findByTenantIdAndBranchId(tenantId, branchId)
                : doorRepository.findByBranchId(branchId);
        return doors.stream().map(this::toResponse).toList();
    }

    @Transactional
    public DoorDTO.Response create(DoorDTO.CreateRequest dto) {
        Long tenantId = TenantContext.getTenantId();
        if (dto.getBranchId() == null) {
            throw new BadRequestException("branchId is required");
        }
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new BadRequestException("name is required");
        }
        Door door = new Door();
        door.setTenantId(tenantId);
        door.setBranchId(dto.getBranchId());
        door.setName(dto.getName().trim());
        door.setStatus(dto.getStatus() != null ? dto.getStatus().trim().toUpperCase() : "ACTIVE");
        Door saved = doorRepository.save(door);
        return toResponse(saved);
    }

    @Transactional
    public DoorDTO.Response update(Long id, DoorDTO.UpdateRequest dto) {
        Long tenantId = TenantContext.getTenantId();
        Door door = doorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Door", id));
        if (tenantId != null && !tenantId.equals(door.getTenantId())) {
            throw new BadRequestException("Door does not belong to your tenant");
        }
        if (dto.getName() != null && !dto.getName().isBlank()) {
            door.setName(dto.getName().trim());
        }
        if (dto.getStatus() != null && !dto.getStatus().isBlank()) {
            door.setStatus(dto.getStatus().trim().toUpperCase());
        }
        Door saved = doorRepository.save(door);
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        Long tenantId = TenantContext.getTenantId();
        Door door = doorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Door", id));
        if (tenantId != null && !tenantId.equals(door.getTenantId())) {
            throw new BadRequestException("Door does not belong to your tenant");
        }
        // Unassign any devices linked to this door before deleting
        deviceConfigRepository.findByDoorId(id).forEach(device -> {
            device.setDoorId(null);
            device.setDoorRole(null);
            deviceConfigRepository.save(device);
        });
        doorRepository.deleteById(id);
    }

    private DoorDTO.Response toResponse(Door door) {
        return new DoorDTO.Response(
                door.getId(),
                door.getTenantId(),
                door.getBranchId(),
                door.getName(),
                door.getStatus(),
                door.getCreatedAt(),
                door.getUpdatedAt()
        );
    }
}
