package com.autobanrobot.server.ban;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class BanEventStream {

    private static final long TIMEOUT_MILLIS = 30 * 60 * 1000L;
    private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(error -> emitters.remove(emitter));
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException error) {
            emitters.remove(emitter);
            emitter.completeWithError(error);
        }
        return emitter;
    }

    public void publish(BanEventResponse event) {
        emitters.removeIf(emitter -> !send(emitter, event));
    }

    private boolean send(SseEmitter emitter, BanEventResponse event) {
        try {
            emitter.send(SseEmitter.event().name("ban").data(event));
            return true;
        } catch (IOException | IllegalStateException error) {
            emitter.complete();
            return false;
        }
    }
}
