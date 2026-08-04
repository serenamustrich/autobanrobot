package com.autobanrobot.server.account;

import org.springframework.http.HttpStatus;

public class AccountApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public AccountApiException(HttpStatus status, String code) {
        super(code);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
}
