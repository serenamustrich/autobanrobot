package com.autobanrobot.server.system;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/version")
public class ServerVersionController {

    @GetMapping
    public ResponseEntity<Map<String, String>> version() {
        String implementationVersion = ServerVersionController.class
            .getPackage()
            .getImplementationVersion();
        String version = implementationVersion == null || implementationVersion.isBlank()
            ? "1.2.5"
            : implementationVersion;
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore().mustRevalidate().sMaxAge(0, TimeUnit.SECONDS))
            .body(Map.of("version", version));
    }
}
