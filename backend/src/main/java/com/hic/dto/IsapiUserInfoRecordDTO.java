package com.hic.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class IsapiUserInfoRecordDTO {
    @JsonProperty("UserInfo")
    private UserInfoDTO userInfo;

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UserInfoDTO {
        private String employeeNo;
        private String name;
        private String userType;
        private String gender;
        private String doorRight;
        @JsonProperty("Valid")
        private ValidityDTO valid;
        @JsonProperty("RightPlan")
        private List<RightPlanDTO> rightPlan;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ValidityDTO {
        private Boolean enable;
        private LocalDateTime beginTime;
        private LocalDateTime endTime;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RightPlanDTO {
        private Integer doorNo;
        private String planTemplateNo;
    }
}
