package com.hic.service;

import com.hic.model.AuditLog;
import com.hic.repository.AuditLogRepository;
import com.hic.util.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Async
    public void log(String action, String entityType, String entityId, String details, String ipAddress) {
        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setTenantId(TenantContext.getTenantId());
            auditLog.setUserId(TenantContext.getUserId());
            auditLog.setUsername(TenantContext.getUsername());
            auditLog.setAction(action);
            auditLog.setEntityType(entityType);
            auditLog.setEntityId(entityId);
            auditLog.setDetails(details);
            auditLog.setIpAddress(ipAddress);
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.warn("Failed to save audit log: {}", e.getMessage());
        }
    }

    @Async
    public void log(String action, String entityType, String entityId, String details) {
        log(action, entityType, entityId, details, null);
    }
}
