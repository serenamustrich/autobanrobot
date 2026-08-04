package com.autobanrobot.server.account;

import jakarta.validation.Valid;
import com.autobanrobot.server.client.ClientIpResolver;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AccountController {

    private final AccountService service;
    private final ClientIpResolver clientIpResolver;

    public AccountController(AccountService service, ClientIpResolver clientIpResolver) {
        this.service = service;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountSessionResponse register(@Valid @RequestBody AccountRegistrationRequest request, HttpServletRequest servletRequest) {
        return service.register(request, clientIpResolver.resolve(servletRequest));
    }

    @PostMapping("/login")
    public AccountSessionResponse login(@Valid @RequestBody AccountCredentialsRequest request, HttpServletRequest servletRequest) {
        return service.login(request, clientIpResolver.resolve(servletRequest));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader(value = "Authorization", required = false) String authorization) { service.logout(authorization); }

    @org.springframework.web.bind.annotation.GetMapping("/me")
    public AccountSessionResponse me(@RequestHeader(value = "Authorization", required = false) String authorization) { return service.me(authorization); }

    @PostMapping("/recovery/question")
    public AccountRecoveryQuestionResponse recoveryQuestion(@Valid @RequestBody AccountUsernameRequest request, HttpServletRequest servletRequest) {
        return service.recoveryQuestion(request, clientIpResolver.resolve(servletRequest));
    }

    @PostMapping("/recovery/reset")
    public AccountSessionResponse resetPassword(@Valid @RequestBody PasswordResetRequest request, HttpServletRequest servletRequest) {
        return service.resetPassword(request, clientIpResolver.resolve(servletRequest));
    }

    @PostMapping("/devices/bind")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void bindDevice(@RequestHeader(value = "Authorization", required = false) String authorization, @Valid @RequestBody DeviceBindRequest request) {
        service.bindDevice(authorization, request);
    }
}
