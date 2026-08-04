package com.autobanrobot.server.account;

import java.util.Set;

public final class SecurityQuestion {

    private static final Set<String> KEYS = Set.of(
        "first_teacher", "childhood_nickname", "first_pet", "favorite_book",
        "favorite_food", "dream_job", "first_concert", "favorite_city",
        "childhood_friend", "favorite_film"
    );

    private SecurityQuestion() { }

    public static boolean isSupported(String key) {
        return key != null && KEYS.contains(key.trim());
    }
}
