package com.autobanrobot.server.release;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@Service
public class ReleaseConfigService {

    private static final long SINGLETON_ID = 1L;
    private static final String RELEASE_PATH_PREFIX =
        "/serenamustrich/autobanrobot/releases/tag/";

    private final ReleaseConfigRepository repository;

    public ReleaseConfigService(ReleaseConfigRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ReleaseConfigResponse current() {
        ReleaseConfig config = repository.findById(SINGLETON_ID).orElseThrow(() ->
            new ResponseStatusException(SERVICE_UNAVAILABLE, "release is not configured")
        );
        return response(config);
    }

    @Transactional
    public synchronized ReleaseConfigResponse update(ReleaseConfigUpdateRequest request) {
        validateGitHubReleaseUrl(request.tag(), request.url());
        Instant updatedAt = Instant.now();
        ReleaseConfig config = repository.findById(SINGLETON_ID).orElseGet(() ->
            new ReleaseConfig(SINGLETON_ID, request.tag(), request.url(), updatedAt)
        );
        config.update(request.tag(), request.url(), updatedAt);
        return response(repository.save(config));
    }

    private void validateGitHubReleaseUrl(String tag, String rawUrl) {
        try {
            URI url = URI.create(rawUrl);
            boolean valid = "https".equalsIgnoreCase(url.getScheme()) &&
                "github.com".equalsIgnoreCase(url.getHost()) &&
                url.getPort() == -1 &&
                url.getRawQuery() == null &&
                url.getRawFragment() == null &&
                (RELEASE_PATH_PREFIX + tag).equals(url.getPath());
            if (!valid) throw new IllegalArgumentException();
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                BAD_REQUEST,
                "url must be the matching AutoBanRobot GitHub Release page"
            );
        }
    }

    private ReleaseConfigResponse response(ReleaseConfig config) {
        return new ReleaseConfigResponse(
            config.getTag(),
            config.getReleaseUrl(),
            config.getUpdatedAt()
        );
    }
}
