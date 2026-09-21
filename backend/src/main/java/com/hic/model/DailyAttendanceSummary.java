package com.hic.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "daily_attendance_summaries")
public class DailyAttendanceSummary {

    public enum AttendanceStatus {
        PRESENT, ABSENT, LATE, EARLY_LEAVE, ON_LEAVE, WORKDAY_COMPLETE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Column(name = "check_in_time")
    private LocalDateTime checkInTime;

    @Column(name = "check_out_time")
    private LocalDateTime checkOutTime;

    @Column(name = "hours_worked")
    private Double hoursWorked;

    @Column(name = "is_standard_day")
    private Boolean isStandardDay;

    @Column(name = "is_additional_day")
    private Boolean isAdditionalDay;

    @Column(name = "is_extra_day")
    private Boolean isExtraDay;

    @Column(name = "is_holiday")
    private Boolean isHoliday;

    @Column(name = "is_leave")
    private Boolean isLeave;

    @Enumerated(EnumType.STRING)
    @Column(name = "attendance_status")
    private AttendanceStatus attendanceStatus;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
