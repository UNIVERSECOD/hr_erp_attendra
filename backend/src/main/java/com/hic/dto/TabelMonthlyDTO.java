package com.hic.dto;

import com.hic.model.Employee.EmploymentStatus;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class TabelMonthlyDTO {
    private int year;
    private int month;
    private int daysInMonth;
    private int employees;
    private List<RowDTO> rows;

    @Data
    public static class RowDTO {
        private Long employeePk;
        private String fin;
        private String fullName;
        private String position;
        private EmploymentStatus employmentStatus;
        private Map<Integer, Object> daily;
        private int workingDays;
        private double totalHours;
    }
}
