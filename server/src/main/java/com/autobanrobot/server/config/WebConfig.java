package com.autobanrobot.server.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOriginPatterns(
                "chrome-extension://*",
                "safari-web-extension://*",
                "http://127.0.0.1:*",
                "http://localhost:*"
            )
            .allowedMethods("GET", "POST", "PUT", "OPTIONS")
            .allowedHeaders("Content-Type", "X-AutoBan-Client", "X-AutoBan-Admin-Token")
            .maxAge(3600);
    }
}
