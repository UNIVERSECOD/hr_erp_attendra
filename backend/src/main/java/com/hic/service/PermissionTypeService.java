package com.hic.service;

import com.hic.dto.PermissionTypeDTO;
import com.hic.exception.ResourceNotFoundException;
import com.hic.model.PermissionType;
import com.hic.repository.PermissionTypeRepository;
import com.hic.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PermissionTypeService {
    private final PermissionTypeRepository permissionTypeRepository;

    public List<PermissionTypeDTO> getAll() {
        Long tenantId = TenantContext.getTenantId();
        List<PermissionType> types = tenantId == null
                ? permissionTypeRepository.findAll()
                : permissionTypeRepository.findByTenantId(tenantId);
        return types.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional
    public PermissionTypeDTO create(PermissionTypeDTO dto) {
        PermissionType pt = toEntity(dto);
        if (pt.getTenantId() == null) {
            Long tenantId = TenantContext.getTenantId();
            if (tenantId != null) {
                pt.setTenantId(tenantId);
            }
        }
        pt.setIsCustom(true);
        return toDTO(permissionTypeRepository.save(pt));
    }

    @Transactional
    public void delete(Long id) {
        PermissionType pt = permissionTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PermissionType", id));
        if (!pt.getIsCustom()) {
            throw new IllegalStateException("Cannot delete built-in permission types");
        }
        permissionTypeRepository.deleteById(id);
    }

    private PermissionTypeDTO toDTO(PermissionType pt) {
        PermissionTypeDTO dto = new PermissionTypeDTO();
        dto.setId(pt.getId());
        dto.setTenantId(pt.getTenantId());
        dto.setCode(pt.getCode());
        dto.setName(pt.getName());
        dto.setIsCustom(pt.getIsCustom());
        return dto;
    }

    private PermissionType toEntity(PermissionTypeDTO dto) {
        PermissionType pt = new PermissionType();
        pt.setTenantId(dto.getTenantId());
        pt.setCode(dto.getCode() != null ? dto.getCode() : dto.getName().toUpperCase().replace(" ", "_"));
        pt.setName(dto.getName());
        return pt;
    }
}
