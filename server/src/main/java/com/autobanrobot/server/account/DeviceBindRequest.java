package com.autobanrobot.server.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeviceBindRequest(@NotBlank @Size(max = 64) String installationId) { }
