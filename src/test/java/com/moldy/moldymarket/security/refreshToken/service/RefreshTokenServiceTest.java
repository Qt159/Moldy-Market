package com.moldy.moldymarket.security.refreshToken.service;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.security.jwt.JwtProperties;
import com.moldy.moldymarket.security.refreshToken.entity.RefreshToken;
import com.moldy.moldymarket.security.refreshToken.repository.RefreshTokenRepository;
import com.moldy.moldymarket.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenService refreshTokenService;
    private User sampleUser;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties("secretKey12345678901234567890123456789012", 900000, 2592000000L);
        refreshTokenService = new RefreshTokenService(refreshTokenRepository, jwtProperties);

        sampleUser = new User();
        sampleUser.setId(UUID.randomUUID());
        sampleUser.setEmail("test@example.com");
    }

    @Test
    @DisplayName("should issue new raw refresh token and persist hash")
    void should_issueToken_when_userProvided() {
        // Act
        String rawToken = refreshTokenService.issueToken(sampleUser);

        // Assert
        assertThat(rawToken).isNotBlank();
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());

        RefreshToken saved = captor.getValue();
        assertThat(saved.getUser()).isEqualTo(sampleUser);
        assertThat(saved.getTokenHash()).isNotBlank();
        assertThat(saved.isRevoked()).isFalse();
        assertThat(saved.getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    @DisplayName("should rotate valid refresh token, revoking old and issuing new")
    void should_rotateToken_when_tokenIsValid() {
        // Arrange
        String rawToken = refreshTokenService.issueToken(sampleUser);
        reset(refreshTokenRepository);

        RefreshToken existing = new RefreshToken(
                sampleUser,
                "some-hash",
                Instant.now().plus(7, ChronoUnit.DAYS)
        );
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(existing));

        // Act
        RefreshTokenService.RotationResult result = refreshTokenService.rotate(rawToken);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.user()).isEqualTo(sampleUser);
        assertThat(result.newRawToken()).isNotBlank().isNotEqualTo(rawToken);
        assertThat(existing.isRevoked()).isTrue();

        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("should throw REFRESH_TOKEN_INVALID when token not found in database")
    void should_throwRefreshTokenInvalid_when_tokenNotFound() {
        // Arrange
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> refreshTokenService.rotate("unknown-raw-token"))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REFRESH_TOKEN_INVALID);

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("should detect reuse of revoked token, revoke all sessions and throw REFRESH_TOKEN_REUSED")
    void should_detectTokenReuseAndRevokeAll_when_tokenAlreadyRevoked() {
        // Arrange
        RefreshToken revokedToken = new RefreshToken(
                sampleUser,
                "revoked-hash",
                Instant.now().plus(7, ChronoUnit.DAYS)
        );
        revokedToken.revoke("replaced-by-hash");

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(revokedToken));

        // Act & Assert
        assertThatThrownBy(() -> refreshTokenService.rotate("reused-token"))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REFRESH_TOKEN_REUSED);

        verify(refreshTokenRepository).revokeAllActiveByUserId(sampleUser.getId());
    }

    @Test
    @DisplayName("should throw REFRESH_TOKEN_EXPIRED when token has expired")
    void should_throwRefreshTokenExpired_when_tokenExpired() {
        // Arrange
        RefreshToken expiredToken = new RefreshToken(
                sampleUser,
                "expired-hash",
                Instant.now().minus(1, ChronoUnit.DAYS)
        );

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(expiredToken));

        // Act & Assert
        assertThatThrownBy(() -> refreshTokenService.rotate("expired-token"))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REFRESH_TOKEN_EXPIRED);
    }

    @Test
    @DisplayName("should revoke token when raw token exists")
    void should_revokeToken_when_rawTokenFound() {
        // Arrange
        RefreshToken existing = new RefreshToken(
                sampleUser,
                "hash-to-revoke",
                Instant.now().plus(7, ChronoUnit.DAYS)
        );
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(existing));

        // Act
        refreshTokenService.revoke("raw-token");

        // Assert
        assertThat(existing.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(existing);
    }

    @Test
    @DisplayName("should revoke all active tokens for given user")
    void should_revokeAllForUser_when_invoked() {
        // Act
        refreshTokenService.revokeAllForUser(sampleUser);

        // Assert
        verify(refreshTokenRepository).revokeAllActiveByUserId(sampleUser.getId());
    }
}
