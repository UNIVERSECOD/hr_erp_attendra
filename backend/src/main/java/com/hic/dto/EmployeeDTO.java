package com.hic.dto;

import com.hic.model.Employee.EmploymentStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class EmployeeDTO {

    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    private LocalDate birthDate;
    private String gender;
    private String mobilePhone;

    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "FIN daxil edilməlidir")
    private String finNumber;
    private String faceId;
    private String cardId;
    private String serialNumber;
    private String contractNumber;
    private Long branchId;

    @NotNull(message = "Department is required")
    private Long departmentId;

    private Long positionId;
    private LocalDate hireDate;
    private LocalDate contractEndDate;
    private Integer annualLeaveDuration;
    private Integer annualLeaveBalance;
    private EmploymentStatus employmentStatus;
    private String fatherName;
    private String groupName;
    private BigDecimal salary;
    private BigDecimal hourlyRate;
    private String allowance;
    private String emergencyContact;
    private String address;
    private String notes;
    private String area;
    private String shiftType;

    @NotNull(message = "Timetable is required")
    private Long timetableId;
    private List<Long> deviceIds;
}
