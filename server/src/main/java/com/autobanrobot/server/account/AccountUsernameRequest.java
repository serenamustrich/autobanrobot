package com.autobanrobot.server.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AccountUsernameRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9_]{3,32}") String username) { }
