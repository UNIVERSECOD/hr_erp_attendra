package com.hic.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IsapiUserInfoCreateRequestDTO {
    @JsonProperty("UserInfo")
    private UserInfoDTO userInfo;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class UserInfoDTO {
        private String employeeNo;
        private String name;
        private String userType;
        private String gender;
        private boolean localUIRight;
        private int maxOpenDoorTime;
        @JsonProperty("Valid")
        private ValidityDTO valid;
        private String doorRight;
        @JsonProperty("RightPlan")
        private List<RightPlanDTO> rightPlan;
        private String userVerifyMode;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidityDTO {
        private boolean enable;
        private String beginTime;
        private String endTime;
        private String timeType;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RightPlanDTO {
        private int doorNo;
        private String planTemplateNo;
    }
}
