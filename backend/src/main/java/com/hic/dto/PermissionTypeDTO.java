package com.hic.dto;

import lombok.Data;

@Data
public class PermissionTypeDTO {
    private Long id;
    private Long tenantId;
    private String code;
    private String name;
    private Boolean isCustom;
}
