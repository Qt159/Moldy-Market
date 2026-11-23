package com.moldy.moldymarket.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "email_verification_otps")
@Getter
@NoArgsConstructor
public class EmailVerificationOtp {

    @Id
    UUID userId;

    @Column(nullable = false)
    String codeHash;

    @Column(nullable = false)
    Instant expiresAt;

    @Column(nullable = false)
    Instant sentAt;

    @Column(nullable = false)
    int failedAttempts;

    public EmailVerificationOtp(UUID userId){
        this.userId = userId;
    }

    public void replaceCode(String codeHash, Instant now) {
        this.codeHash = codeHash;
        this.sentAt = now;
        this.expiresAt = now.plusSeconds(300);
        this.failedAttempts = 0;
    }

    public void recordFailedAttempt() {
        failedAttempts++;
    }

}
