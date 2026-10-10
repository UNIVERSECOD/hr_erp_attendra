package com.hic.service;

import com.hic.exception.BadRequestException;
import com.hic.exception.ResourceNotFoundException;
import com.hic.model.DeviceConfig;
import com.hic.repository.BranchRepository;
import com.hic.repository.DeviceConfigRepository;
import com.hic.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeviceAreaService {
    private final DeviceConfigRepository devices;
    private final BranchRepository branches;
    private final EmployeeAreaAssignmentService assignments;

    /** Local metadata only; never sends commands to a physical terminal. */
    @Transactional
    public void assign(Long deviceId, Long branchId) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) throw new BadRequestException("Şirkət məlumatı tələb olunur.");
        DeviceConfig device = devices.findById(deviceId)
                .filter(d -> tenantId.equals(d.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Cihaz", deviceId));
        if (branchId != null) branches.findByIdAndTenantId(branchId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Ərazi", branchId));
        if (java.util.Objects.equals(device.getBranchId(), branchId)) return;
        device.setBranchId(branchId);
        // A door belongs to the previous area. Preserve direction, clear only its door link.
        device.setDoorId(null);
        devices.save(device);
        assignments.reconcileDeviceAreaAccess(device);
    }
}
