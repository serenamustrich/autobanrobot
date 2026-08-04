package com.autobanrobot.server.account;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AccountServiceTest {

    private final AccountRepository accounts = mock(AccountRepository.class);
    private final AccountSessionRepository sessions = mock(AccountSessionRepository.class);
    private final AccountSettingsRepository settings = mock(AccountSettingsRepository.class);
    private final AccountDeviceRepository devices = mock(AccountDeviceRepository.class);
    private final AccountSettingsStream stream = mock(AccountSettingsStream.class);
    private final AccountRateLimitService rateLimits = mock(AccountRateLimitService.class);
    private final AccountHistoryClaimService claims = mock(AccountHistoryClaimService.class);
    private final AccountService service = new AccountService(accounts, sessions, settings, devices, stream, rateLimits, claims);

    @Test
    void rejectsUnsupportedSecurityQuestionBeforePersistingAccount() {
        AccountRegistrationRequest request = new AccountRegistrationRequest("tester", "password-1", "not-a-question", "answer");
        AccountApiException error = assertThrows(AccountApiException.class, () -> service.register(request, "127.0.0.1"));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        assertEquals("AUTH_SECURITY_QUESTION_INVALID", error.getCode());
        verify(accounts, never()).saveAndFlush(any());
    }

    @Test
    void loginReturnsNewOpaqueSessionForValidPassword() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        Account account = new Account("tester", encoder.encode("password-1"), "first_teacher", encoder.encode("answer"), Instant.now());
        when(accounts.findByUsername("tester")).thenReturn(Optional.of(account));

        AccountSessionResponse response = service.login(new AccountCredentialsRequest("Tester", "password-1"), "127.0.0.1");

        assertEquals("tester", response.username());
        assertTrue(response.accessToken().length() >= 32);
        verify(sessions).save(any(AccountSession.class));
    }

    @Test
    void recoveryRejectsWrongAnswerWithoutDeletingSessions() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        Account account = new Account("tester", encoder.encode("password-1"), "first_teacher", encoder.encode("correct"), Instant.now());
        when(accounts.findByUsername("tester")).thenReturn(Optional.of(account));
        PasswordResetRequest request = new PasswordResetRequest("tester", "first_teacher", "wrong", "password-2");

        AccountApiException error = assertThrows(AccountApiException.class, () -> service.resetPassword(request, "127.0.0.1"));

        assertEquals("AUTH_RECOVERY_INVALID", error.getCode());
        verify(sessions, never()).deleteByAccountId(any());
    }
}
