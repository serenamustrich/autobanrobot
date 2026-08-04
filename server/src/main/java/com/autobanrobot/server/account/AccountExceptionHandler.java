package com.autobanrobot.server.account;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.HttpStatus;

@RestControllerAdvice(assignableTypes = {AccountController.class, AccountSettingsController.class})
public class AccountExceptionHandler {

    @ExceptionHandler(AccountApiException.class)
    public ResponseEntity<AccountErrorResponse> accountError(AccountApiException error) {
        return ResponseEntity.status(error.getStatus()).body(new AccountErrorResponse(error.getCode()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<AccountErrorResponse> validationError(MethodArgumentNotValidException error) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new AccountErrorResponse("AUTH_VALIDATION_FAILED"));
    }
}
