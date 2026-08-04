package com.autobanrobot.server.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "autoban_account", uniqueConstraints = @UniqueConstraint(name = "uk_autoban_account_username", columnNames = "username"))
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "security_question_key", nullable = false, length = 48)
    private String securityQuestionKey;

    @Column(name = "security_answer_hash", nullable = false, length = 100)
    private String securityAnswerHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Account() {
    }

    public Account(
        String username,
        String passwordHash,
        String securityQuestionKey,
        String securityAnswerHash,
        Instant createdAt
    ) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.securityQuestionKey = securityQuestionKey;
        this.securityAnswerHash = securityAnswerHash;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getSecurityQuestionKey() { return securityQuestionKey; }
    public String getSecurityAnswerHash() { return securityAnswerHash; }

    public void resetPassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }
}
