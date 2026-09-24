package com.hic.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "attendance_logs")
public class AttendanceLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "check_in_time")
    private LocalDateTime checkInTime;

    @Column(name = "check_out_time")
    private LocalDateTime checkOutTime;

    @Column(name = "device_id")
    private String deviceId;

    @Column(name = "door_id")
    private String doorId;

    @Column(name = "event_type")
    private String eventType;

    @Column(name = "verification_method")
    private String verificationMethod;

    /**
     * Schedule type frozen at session creation (or backfilled from assignment history).
     * Keeps historical punches under the schedule they were worked on.
     */
    @Column(name = "shift_type")
    private String shiftType;

    @Column(name = "timetable_id")
    private Long timetableId;

    @Column(name = "entry_punch_id")
    private Long entryPunchId;

    @Column(name = "exit_punch_id")
    private Long exitPunchId;

    @Column(name = "manual_override", nullable = false)
    private Boolean manualOverride = false;

    @Column(name = "status")
    private String status;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
