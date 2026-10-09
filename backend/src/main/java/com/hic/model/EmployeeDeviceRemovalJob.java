package com.hic.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "employee_device_removal_jobs")
public class EmployeeDeviceRemovalJob {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long tenantId;
    private Long employeeId;
    private Long deviceConfigId;
    private String bridgeDeviceId;
    private String deviceIp;
    private String deviceName;
    private String employeeNo;
    private boolean completed;
    private int attempts;
    private String lastError;
    private LocalDateTime nextAttemptAt;
    private LocalDateTime completedAt;
}
