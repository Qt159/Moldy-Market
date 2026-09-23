package com.moldy.moldymarket.security.refreshToken.entity;

import com.moldy.moldymarket.user.entity.User;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "refresh_tokens",
        indexes = {
                @Index(name = "idx_refresh_tokens_user_id", columnList = "user_id"),
                @Index(name = "idx_refresh_tokens_token_hash", columnList = "token_hash", unique = true)
        }
)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 128)
    String tokenHash;

    @Column(name = "expires_at", nullable = false)
    Instant expiresAt;

    @Column(name = "revoked", nullable = false)
    boolean revoked = false;

    @Column(name = "replaced_by_token_hash", length = 128)
    String replacedByTokenHash;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    protected RefreshToken() {}

    public RefreshToken(User user, String tokenHash, Instant expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public String getTokenHash() { return tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isRevoked() { return revoked; }
    public String getReplacedByTokenHash() { return replacedByTokenHash; }
    public Instant getCreatedAt() { return createdAt; }

    public void revoke(String replacedByTokenHash) {
        this.revoked = true;
        this.replacedByTokenHash = replacedByTokenHash;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
