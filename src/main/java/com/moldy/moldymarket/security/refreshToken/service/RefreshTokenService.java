package com.moldy.moldymarket.security.refreshToken.service;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.security.jwt.JwtProperties;
import com.moldy.moldymarket.security.refreshToken.entity.RefreshToken;
import com.moldy.moldymarket.security.refreshToken.repository.RefreshTokenRepository;
import com.moldy.moldymarket.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class RefreshTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final Duration refreshTokenTtl;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            JwtProperties jwtProperties
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenTtl = Duration.ofMillis(jwtProperties.refreshTokenExpiration());
    }

    @Transactional
    public String issueToken(User user) {
        String rawToken = generateRawToken();
        String hash = hash(rawToken);

        refreshTokenRepository.save(
                new RefreshToken(user, hash, Instant.now().plus(refreshTokenTtl))
        );

        return rawToken;
    }

    @Transactional
    public RotationResult rotate(String rawToken) {
        String hash = hash(rawToken);

        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new AppException(
                        ErrorCode.REFRESH_TOKEN_INVALID, "Refresh token not recognized"
                ));

        if (existing.isRevoked()) {
            refreshTokenRepository.revokeAllActiveByUserId(existing.getUser().getId());
            throw new AppException(
                    ErrorCode.REFRESH_TOKEN_REUSED,
                    "Refresh token reuse detected, all sessions revoked"
            );
        }

        if (existing.isExpired()) {
            throw new AppException(ErrorCode.REFRESH_TOKEN_EXPIRED, "Refresh token expired");
        }

        String newRawToken = generateRawToken();
        String newHash = hash(newRawToken);

        existing.revoke(newHash);
        refreshTokenRepository.save(existing);

        refreshTokenRepository.save(
                new RefreshToken(existing.getUser(), newHash, Instant.now().plus(refreshTokenTtl))
        );

        return new RotationResult(existing.getUser(), newRawToken);
    }

    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken))
                .ifPresent(token -> {
                    token.revoke(null);
                    refreshTokenRepository.save(token);
                });
    }

    @Transactional
    public void revokeAllForUser(User user) {
        refreshTokenRepository.revokeAllActiveByUserId(user.getId());
    }

    private String generateRawToken() {
        byte[] bytes = new byte[64];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public record RotationResult(User user, String newRawToken) {}
}