package com.hic.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BranchDTO {

    private Long id;

    @NotBlank(message = "Name is required")
    private String name;

    private String code;

    private String city;
    private String address;
    private String status;
    private Boolean isHeadOffice;
}
