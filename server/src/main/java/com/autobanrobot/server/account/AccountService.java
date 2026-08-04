package com.autobanrobot.server.account;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class AccountService {

    private static final Duration SESSION_LIFETIME = Duration.ofDays(90);
    private final AccountRepository accounts;
    private final AccountSessionRepository sessions;
    private final AccountSettingsRepository settings;
    private final AccountDeviceRepository devices;
    private final AccountSettingsStream settingsStream;
    private final AccountRateLimitService rateLimits;
    private final AccountHistoryClaimService historyClaims;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();

    public AccountService(
        AccountRepository accounts,
        AccountSessionRepository sessions,
        AccountSettingsRepository settings,
        AccountDeviceRepository devices,
        AccountSettingsStream settingsStream,
        AccountRateLimitService rateLimits,
        AccountHistoryClaimService historyClaims
    ) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.settings = settings;
        this.devices = devices;
        this.settingsStream = settingsStream;
        this.rateLimits = rateLimits;
        this.historyClaims = historyClaims;
    }

    @Transactional
    public AccountSessionResponse register(AccountRegistrationRequest request, String clientIp) {
        String username = normalizeUsername(request.username());
        rateLimits.check("register", username, clientIp, 5);
        if (accounts.findByUsername(username).isPresent()) {
            throw new AccountApiException(HttpStatus.CONFLICT, "AUTH_USERNAME_EXISTS");
        }
        if (!SecurityQuestion.isSupported(request.securityQuestionKey())) {
            throw new AccountApiException(HttpStatus.BAD_REQUEST, "AUTH_SECURITY_QUESTION_INVALID");
        }
        Account account = accounts.saveAndFlush(new Account(
            username, passwordEncoder.encode(request.password()), request.securityQuestionKey().trim(),
            passwordEncoder.encode(normalizeAnswer(request.securityAnswer())), Instant.now()
        ));
        return createSession(account);
    }

    @Transactional
    public AccountSessionResponse login(AccountCredentialsRequest request, String clientIp) {
        String username = normalizeUsername(request.username());
        rateLimits.check("login", username, clientIp, 8);
        Account account = accounts.findByUsername(username)
            .orElseThrow(() -> new AccountApiException(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_CREDENTIALS"));
        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw new AccountApiException(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_CREDENTIALS");
        }
        rateLimits.clear("login", username, clientIp);
        return createSession(account);
    }

    @Transactional(readOnly = true)
    public Account requireAccount(String authorization) {
        String token = bearerToken(authorization);
        AccountSession session = sessions.findByTokenHash(hash(token))
            .filter(value -> value.getExpiresAt().isAfter(Instant.now()))
            .orElseThrow(() -> new AccountApiException(HttpStatus.UNAUTHORIZED, "AUTH_SESSION_EXPIRED"));
        return accounts.findById(session.getAccountId())
            .orElseThrow(() -> new AccountApiException(HttpStatus.UNAUTHORIZED, "AUTH_SESSION_EXPIRED"));
    }

    @Transactional(readOnly = true)
    public AccountSessionResponse me(String authorization) {
        Account account = requireAccount(authorization);
        AccountSession session = sessions.findByTokenHash(hash(bearerToken(authorization)))
            .orElseThrow(() -> new AccountApiException(HttpStatus.UNAUTHORIZED, "AUTH_SESSION_EXPIRED"));
        return new AccountSessionResponse("", account.getUsername(), session.getExpiresAt());
    }

    @Transactional
    public void logout(String authorization) { sessions.deleteByTokenHash(hash(bearerToken(authorization))); }

    @Transactional(readOnly = true)
    public AccountRecoveryQuestionResponse recoveryQuestion(AccountUsernameRequest request, String clientIp) {
        String username = normalizeUsername(request.username());
        rateLimits.check("recovery-question", username, clientIp, 5);
        Account account = accounts.findByUsername(username)
            .orElseThrow(() -> new AccountApiException(HttpStatus.NOT_FOUND, "AUTH_ACCOUNT_NOT_FOUND"));
        return new AccountRecoveryQuestionResponse(account.getSecurityQuestionKey());
    }

    @Transactional
    public AccountSessionResponse resetPassword(PasswordResetRequest request, String clientIp) {
        String username = normalizeUsername(request.username());
        rateLimits.check("password-reset", username, clientIp, 5);
        Account account = accounts.findByUsername(username)
            .orElseThrow(() -> new AccountApiException(HttpStatus.UNAUTHORIZED, "AUTH_RECOVERY_INVALID"));
        if (!account.getSecurityQuestionKey().equals(request.securityQuestionKey()) ||
            !passwordEncoder.matches(normalizeAnswer(request.securityAnswer()), account.getSecurityAnswerHash())) {
            throw new AccountApiException(HttpStatus.UNAUTHORIZED, "AUTH_RECOVERY_INVALID");
        }
        account.resetPassword(passwordEncoder.encode(request.newPassword()));
        sessions.deleteByAccountId(account.getId());
        rateLimits.clear("password-reset", username, clientIp);
        return createSession(account);
    }

    @Transactional
    public void bindDevice(String authorization, DeviceBindRequest request) {
        Account account = requireAccount(authorization);
        String installationId = request.installationId().trim();
        AccountDevice device = devices.findByInstallationId(installationId)
            .orElse(new AccountDevice(account.getId(), installationId, Instant.now()));
        device.bindTo(account.getId(), Instant.now());
        devices.save(device);
        historyClaims.claim(account.getId(), installationId);
    }

    @Transactional(readOnly = true)
    public Account findByInstallationId(String installationId) {
        return devices.findByInstallationId(installationId)
            .flatMap(device -> accounts.findById(device.getAccountId())).orElse(null);
    }

    @Transactional(readOnly = true)
    public AccountSettingsResponse settings(String authorization) {
        Account account = requireAccount(authorization);
        return settings.findById(account.getId()).map(this::response)
            .orElseGet(() -> new AccountSettingsResponse(List.of(), List.of(), Instant.EPOCH, 0));
    }

    @Transactional
    public AccountSettingsResponse replaceSettings(String authorization, AccountSettingsRequest request, boolean merge) {
        Account account = requireAccount(authorization);
        AccountSettings current = settings.findById(account.getId())
            .orElse(new AccountSettings(account.getId(), "", "", Instant.EPOCH));
        List<String> keywords = merge
            ? mergeValues(values(current.getKeywords()), request.keywords(), false)
            : normalizeValues(request.keywords(), false);
        List<String> whitelist = merge
            ? mergeValues(values(current.getWhitelist()), request.whitelist(), true)
            : normalizeValues(request.whitelist(), true);
        current.replace(join(keywords), join(whitelist), Instant.now());
        AccountSettingsResponse response = response(settings.save(current));
        settingsStream.publish(account.getId(), response);
        return response;
    }

    private AccountSessionResponse createSession(Account account) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = Instant.now().plus(SESSION_LIFETIME);
        sessions.save(new AccountSession(account.getId(), hash(token), expiresAt));
        return new AccountSessionResponse(token, account.getUsername(), expiresAt);
    }

    private String normalizeUsername(String username) {
        return username.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private String bearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new AccountApiException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED");
        }
        String token = authorization.substring("Bearer ".length()).trim();
        if (token.length() < 32 || token.length() > 256) {
            throw new AccountApiException(HttpStatus.UNAUTHORIZED, "AUTH_SESSION_EXPIRED");
        }
        return token;
    }

    private String hash(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private AccountSettingsResponse response(AccountSettings value) {
        return new AccountSettingsResponse(values(value.getKeywords()), values(value.getWhitelist()), value.getUpdatedAt(), value.getSettingsRevision());
    }

    private List<String> mergeValues(List<String> stored, List<String> incoming, boolean lowercase) {
        return normalizeValues(java.util.stream.Stream.concat(stored.stream(), (incoming == null ? List.<String>of() : incoming).stream()).toList(), lowercase);
    }

    private List<String> normalizeValues(List<String> values, boolean lowercase) {
        if (values == null) return List.of();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String raw : values) {
            if (raw == null) continue;
            String value = raw.trim();
            if (lowercase) value = value.toLowerCase(java.util.Locale.ROOT);
            if (!value.isEmpty()) result.add(value);
            if (result.size() == 1000) break;
        }
        return List.copyOf(result);
    }

    private List<String> values(String joined) {
        if (joined == null || joined.isBlank()) return List.of();
        return Arrays.stream(joined.split("\\n")).filter(value -> !value.isBlank()).toList();
    }

    private String join(List<String> values) {
        return String.join("\n", values);
    }

    private String normalizeAnswer(String value) {
        return java.text.Normalizer.normalize(value.trim().replaceAll("\\s+", " "), java.text.Normalizer.Form.NFKC)
            .toLowerCase(java.util.Locale.ROOT);
    }
}
