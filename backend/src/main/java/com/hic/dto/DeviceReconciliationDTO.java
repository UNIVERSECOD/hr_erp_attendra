package com.hic.dto;

import java.time.Instant;
import java.util.List;

public class DeviceReconciliationDTO {
    public record Row(Long employeeId, String employeeNo, String name, String action, String reason) {}
    public record Preview(String token, Instant expiresAt, String deviceName, String deviceIp, Long areaId,
                          List<Row> rows, int operations) {}
    public record ApplyRequest(String token, int step, boolean confirmed) {}
    public record Outcome(Row row, String status, String message) {}
    public record Progress(int nextStep, boolean finished, List<Outcome> outcomes) {}
}
