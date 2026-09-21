package com.hic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePasswordRequest {

    @NotBlank(message = "Current password is required")
    @Size(max = 128, message = "Current password must not exceed 128 characters")
    private String currentPassword;

    @NotBlank(message = "New password is required")
    @Size(min = 8, max = 128, message = "New password must be between 8 and 128 characters")
    @Pattern(regexp = ".*\\d.*", message = "New password must contain at least one digit")
    private String newPassword;

    @NotBlank(message = "Password confirmation is required")
    @Size(max = 128, message = "Password confirmation must not exceed 128 characters")
    private String confirmPassword;
}
