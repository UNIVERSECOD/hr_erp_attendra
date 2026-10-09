package com.hic.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeTerminationResultDTO {
    private Long employeeId;
    private int totalDevices;
    private int removedDevices;
    private int failedDevices;
    private List<String> errors = new ArrayList<>();
}
