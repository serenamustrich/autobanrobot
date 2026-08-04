package com.autobanrobot.server.admin;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
public class AdminPortalController {

    private final AdminPortalAccessService access;
    private final AdminOverviewService overview;

    public AdminPortalController(AdminPortalAccessService access, AdminOverviewService overview) {
        this.access = access;
        this.overview = overview;
    }

    @GetMapping(value = "/{firstToken}/{secondToken}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> page(
        @PathVariable String firstToken,
        @PathVariable String secondToken
    ) {
        access.requireAccess(firstToken, secondToken);
        return protectedResponse().contentType(MediaType.TEXT_HTML)
            .body(new ClassPathResource("static/admin-portal.html"));
    }

    @GetMapping(value = "/{firstToken}/{secondToken}/api/overview", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AdminOverviewResponse> overview(
        @PathVariable String firstToken,
        @PathVariable String secondToken
    ) {
        access.requireAccess(firstToken, secondToken);
        return protectedResponse().contentType(MediaType.APPLICATION_JSON).body(overview.overview());
    }

    private ResponseEntity.BodyBuilder protectedResponse() {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore().mustRevalidate().sMaxAge(0, TimeUnit.SECONDS))
            .header("Referrer-Policy", "no-referrer")
            .header("X-Frame-Options", "DENY")
            .header("Content-Security-Policy", "default-src 'self'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; connect-src 'self'; base-uri 'none'; frame-ancestors 'none'");
    }
}
