package com.autobanrobot.server.account;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AccountSettingsStream {
    private final ConcurrentHashMap<Long, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();
    public SseEmitter subscribe(Long accountId) {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.computeIfAbsent(accountId, ignored -> ConcurrentHashMap.newKeySet()).add(emitter);
        Runnable cleanup = () -> emitters.computeIfPresent(accountId, (id, values) -> { values.remove(emitter); return values.isEmpty() ? null : values; });
        emitter.onCompletion(cleanup); emitter.onTimeout(cleanup); emitter.onError(ignored -> cleanup.run());
        return emitter;
    }
    public void publish(Long accountId, AccountSettingsResponse settings) {
        for (SseEmitter emitter : emitters.getOrDefault(accountId, Set.of())) {
            try { emitter.send(SseEmitter.event().name("settings").data(settings)); }
            catch (IOException error) { emitter.complete(); }
        }
    }
}
