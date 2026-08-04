package com.autobanrobot.server.account;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AccountRateLimitService {
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();
    public void check(String action, String subject, String ip, int limit) {
        String key = action + ':' + subject + ':' + ip;
        Instant now = Instant.now();
        Attempt next = attempts.compute(key, (ignored, current) -> current == null || current.startedAt.plus(WINDOW).isBefore(now) ? new Attempt(now, 1) : new Attempt(current.startedAt, current.count + 1));
        if (next.count > limit) throw new AccountApiException(HttpStatus.TOO_MANY_REQUESTS, "AUTH_RATE_LIMITED");
    }
    public void clear(String action, String subject, String ip) { attempts.remove(action + ':' + subject + ':' + ip); }
    private record Attempt(Instant startedAt, int count) { }
}
