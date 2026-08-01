package com.autobanrobot.server.ban;

import java.util.List;

public record BanPageResponse(
    List<BanEventResponse> items,
    long total,
    int page,
    int size,
    int totalPages
) {
}
