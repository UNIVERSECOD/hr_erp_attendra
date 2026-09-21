package com.hic.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "holiday_permissions")
public class HolidayPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "apply_scope", nullable = false)
    private String applyScope = "COMPANY";

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "target_ids", columnDefinition = "bigint[]")
    private Long[] targetIds;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "employee_ids", columnDefinition = "bigint[]")
    private Long[] employeeIds;

    @Column(nullable = false)
    private String status = "ACTIVE";

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (targetIds == null) targetIds = new Long[0];
        if (employeeIds == null) employeeIds = new Long[0];
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        if (targetIds == null) targetIds = new Long[0];
        if (employeeIds == null) employeeIds = new Long[0];
        updatedAt = LocalDateTime.now();
    }
}
