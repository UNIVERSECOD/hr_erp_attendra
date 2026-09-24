package com.hic.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "attendance_log_adjustments")
public class AttendanceLogAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "attendance_log_id", nullable = false)
    private Long attendanceLogId;

    @Column(name = "previous_check_in_time")
    private LocalDateTime previousCheckInTime;

    @Column(name = "previous_check_out_time")
    private LocalDateTime previousCheckOutTime;

    @Column(name = "new_check_in_time", nullable = false)
    private LocalDateTime newCheckInTime;

    @Column(name = "new_check_out_time")
    private LocalDateTime newCheckOutTime;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "corrected_by", nullable = false, length = 100)
    private String correctedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
