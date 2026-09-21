package com.hic.repository;

import com.hic.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    Page<AuditLog> findByTenantIdOrderByCreatedAtDesc(Long tenantId, Pageable pageable);
    Page<AuditLog> findByTenantIdAndUsernameOrderByCreatedAtDesc(Long tenantId, String username, Pageable pageable);
}
