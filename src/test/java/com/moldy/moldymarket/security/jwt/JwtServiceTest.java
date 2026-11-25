package com.moldy.moldymarket.security.jwt;

import com.moldy.moldymarket.user.entity.User;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;

    @org.junit.jupiter.api.Test
    void rejectsJwtSecretsShorterThan256Bits() {
        var properties = new JwtProperties("too-short-secret", 900_000, 2_592_000_000L);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new JwtService(properties));
    }
    private User sampleUser;
    private UUID userId;

    @BeforeEach
    void setUp() {
        // 256-bit secret key (at least 32 characters)
        JwtProperties properties = new JwtProperties(
                "mySuperSecureTestSecretKeyForTestingJwtService32Bytes!",
                3600000, // 1 hour
                2592000000L
        );
        jwtService = new JwtService(properties);

        userId = UUID.randomUUID();
        sampleUser = new User();
        sampleUser.setId(userId);
        sampleUser.setEmail("user@example.com");
    }

    @Test
    @DisplayName("should generate valid access token containing user claims")
    void should_generateValidAccessToken_when_userProvided() {
        // Act
        String token = jwtService.generateAccessToken(sampleUser);

        // Assert
        assertThat(token).isNotBlank();
        assertThat(jwtService.isTokenValid(token)).isTrue();
        assertThat(jwtService.extractUserId(token)).isEqualTo(userId);

        Claims claims = jwtService.extractAllClaims(token);
        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.get("email", String.class)).isEqualTo("user@example.com");
        assertThat(claims.get("tokenType", String.class)).isEqualTo("ACCESS");
    }

    @Test
    @DisplayName("should return false when token is invalid or malformed")
    void should_returnFalse_when_tokenIsMalformed() {
        // Act & Assert
        assertThat(jwtService.isTokenValid("not.a.valid.jwt.token")).isFalse();
        assertThat(jwtService.isTokenValid("")).isFalse();
        assertThat(jwtService.isTokenValid(null)).isFalse();
    }

    @Test
    @DisplayName("should return false when token is expired")
    void should_returnFalse_when_tokenIsExpired() {
        // Arrange: service with negative expiration
        JwtProperties expiredProps = new JwtProperties(
                "mySuperSecureTestSecretKeyForTestingJwtService32Bytes!",
                -1000,
                2592000000L
        );
        JwtService expiredJwtService = new JwtService(expiredProps);

        // Act
        String expiredToken = expiredJwtService.generateAccessToken(sampleUser);

        // Assert
        assertThat(jwtService.isTokenValid(expiredToken)).isFalse();
    }

    @Test
    @DisplayName("should return false when signed with different key")
    void should_returnFalse_when_signatureMismatch() {
        // Arrange
        JwtProperties otherProps = new JwtProperties(
                "anotherDifferentSuperSecretKeyForTestingJwtService32B!",
                3600000,
                2592000000L
        );
        JwtService otherJwtService = new JwtService(otherProps);
        String tokenFromOther = otherJwtService.generateAccessToken(sampleUser);

        // Act & Assert
        assertThat(jwtService.isTokenValid(tokenFromOther)).isFalse();
    }
}
