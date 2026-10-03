package com.hic.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

public class DeviceEmployeeAssignmentDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmployeeOption {
        private Long employeeId;
        private String employeeCode;
        private String fullName;
        private String finNumber;
        private List<Long> areaIds;
        private List<String> areaNames;
        private boolean areaAssigned;
        private boolean manuallyAssigned;
        private boolean assigned;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssignmentView {
        private Long deviceConfigId;
        private String deviceName;
        private Long areaId;
        private String areaName;
        private List<EmployeeOption> employees = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {
        private List<Long> manualEmployeeIds = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SyncResult {
        private Long deviceConfigId;
        private int total;
        private int succeeded;
        private int failed;
        private int facesSynced;
        private int facesSkipped;
        private int facesFailed;
        private List<String> errors = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmployeeSyncResult {
        private Long employeeId;
        private int totalDevices;
        private int usersSynced;
        private int facesSynced;
        private int facesSkipped;
        private int failedDevices;
        private List<String> errors = new ArrayList<>();
    }
}
