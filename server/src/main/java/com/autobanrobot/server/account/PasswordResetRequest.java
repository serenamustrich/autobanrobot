package com.autobanrobot.server.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PasswordResetRequest(
    @NotBlank @Pattern(regexp = "[A-Za-z0-9_]{3,32}") String username,
    @NotBlank @Size(max = 48) String securityQuestionKey,
    @NotBlank @Size(min = 2, max = 128) String securityAnswer,
    @NotBlank @Size(min = 8, max = 128) String newPassword
) { }
