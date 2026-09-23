package com.moldy.moldymarket.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    String email;

    @Column(name = "phone", length = 30)
    String phone;

    @Column(name = "password_hash", length = 255)
    String passwordHash;

    @Column(name = "google_id", unique = true, length = 255)
    String googleId;

    @Column(name = "full_name", nullable = false, length = 150)
    String fullName;

    @Setter
    @Column(name = "avatar_url", length = 1000)
    String avatarUrl;

    @Column(name = "trust_points", nullable = false)
    Integer trustPoints = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    UserStatus status = UserStatus.PENDING_VERIFICATION;

    @Column(name = "bank_name", length = 150)
    String bankName;

    @Column(name = "bank_account_number", length = 100)
    String bankAccountNumber;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();

        this.createdAt = now;
        this.updatedAt = now;

        if (this.trustPoints == null) {
            this.trustPoints = 0;
        }

        if (this.status == null) {
            this.status = UserStatus.PENDING_VERIFICATION;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void setGoogleId(String googleId) {
        this.googleId = googleId;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public void setBankAccountNumber(String bankAccountNumber) {
        this.bankAccountNumber = bankAccountNumber;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public void setTrustPoints(Integer trustPoints) {
        this.trustPoints = trustPoints;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

}
