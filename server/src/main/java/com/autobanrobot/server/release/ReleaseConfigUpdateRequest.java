package com.autobanrobot.server.release;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ReleaseConfigUpdateRequest(
    @NotBlank @Pattern(regexp = "v[0-9]+(?:\\.[0-9]+)+") String tag,
    @NotBlank @Size(max = 512) String url
) {
}
