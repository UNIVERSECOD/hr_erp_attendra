package com.hic.dto;

import lombok.Data;

import java.util.List;

@Data
public class HolidayDTO {
    private Long id;
    private Long tenantId;
    private String name;
    private String description;
    private String holidayDate;
    private String applyScope;
    private List<Long> targetIds;
    private String scopeType;
}
