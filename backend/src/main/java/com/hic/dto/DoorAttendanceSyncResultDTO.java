package com.hic.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class DoorAttendanceSyncResultDTO {
    private int totalPunches;
    private int matchedSessions;
    private int createdLogs;
    private int skippedEmployees;
    private int skippedPunches;
    private int recalculatedDays;
    private List<String> unresolvedEmployeeNos;
}
