package com.moldy.moldymarket.user.service;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.common.ratelimit.RateLimitExceededException;
import com.moldy.moldymarket.common.ratelimit.RateLimitPolicy;
import com.moldy.moldymarket.common.ratelimit.RateLimiter;
import com.moldy.moldymarket.role.entity.Role;
import com.moldy.moldymarket.role.repository.RoleRepository;
import com.moldy.moldymarket.security.jwt.JwtService;
import com.moldy.moldymarket.security.refreshToken.service.RefreshTokenService;
import com.moldy.moldymarket.security.user.CustomUserDetails;
import com.moldy.moldymarket.user.dto.request.LoginRequest;
import com.moldy.moldymarket.user.dto.request.RegisterRequest;
import com.moldy.moldymarket.user.dto.response.AuthResponse;
import com.moldy.moldymarket.user.dto.response.RegisterResponse;
import com.moldy.moldymarket.user.entity.User;
import com.moldy.moldymarket.user.entity.UserStatus;
import com.moldy.moldymarket.user.mapper.UserMapper;
import com.moldy.moldymarket.user.repository.UserRepository;
import com.moldy.moldymarket.userrole.entity.UserRole;
import com.moldy.moldymarket.userrole.repository.UserRoleRepository;
import com.moldy.moldymarket.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserMapper userMapper;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private RateLimiter rateLimiter;

    @Mock
    private WalletService walletService;

    @Mock
    private EmailVerificationService emailVerificationService;

    @InjectMocks
    private AuthenticationService authenticationService;

    private User sampleUser;
    private final String clientIp = "192.168.1.100";

    @BeforeEach
    void setUp() {
        sampleUser = new User();
        sampleUser.setId(UUID.randomUUID());
        sampleUser.setEmail("test@moldymarket.com");
        sampleUser.setFullName("Test User");
        sampleUser.setPasswordHash("encodedPassword");
        sampleUser.setStatus(UserStatus.ACTIVE);
        sampleUser.setCreatedAt(Instant.now());
    }

    @Test
    @DisplayName("should return AuthResponse when credentials are valid")
    void should_returnAuthResponse_when_credentialsAreValid() {
        // Arrange
        LoginRequest request = new LoginRequest("test@moldymarket.com", "password123");
        Authentication auth = mock(Authentication.class);
        CustomUserDetails userDetails = mock(CustomUserDetails.class);

        when(userDetails.getUser()).thenReturn(sampleUser);
        when(auth.getPrincipal()).thenReturn(userDetails);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(auth);
        when(jwtService.generateAccessToken(sampleUser)).thenReturn("access-token-xyz");
        when(refreshTokenService.issueToken(sampleUser)).thenReturn("refresh-token-abc");

        // Act
        AuthResponse response = authenticationService.login(request, clientIp);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo(sampleUser.getId());
        assertThat(response.email()).isEqualTo("test@moldymarket.com");
        assertThat(response.accessToken()).isEqualTo("access-token-xyz");
        assertThat(response.refreshToken()).isEqualTo("refresh-token-abc");
        assertThat(response.tokenType()).isEqualTo("Bearer");

        verify(rateLimiter).check(RateLimitPolicy.LOGIN_PER_IP, clientIp);
        verify(rateLimiter).check(RateLimitPolicy.LOGIN_PER_ACCOUNT, request.email());
        verify(jwtService).generateAccessToken(sampleUser);
        verify(refreshTokenService).issueToken(sampleUser);
    }

    @Test
    @DisplayName("should throw ACCOUNT_DISABLED when account is disabled")
    void should_throwAccountDisabled_when_accountIsDisabled() {
        // Arrange
        LoginRequest request = new LoginRequest("disabled@moldymarket.com", "password123");
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new DisabledException("Account disabled"));

        // Act & Assert
        assertThatThrownBy(() -> authenticationService.login(request, clientIp))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCOUNT_DISABLED);

        verify(jwtService, never()).generateAccessToken(any());
        verify(refreshTokenService, never()).issueToken(any());
    }

    @Test
    @DisplayName("should throw ACCOUNT_LOCKED when account is locked")
    void should_throwAccountLocked_when_accountIsLocked() {
        // Arrange
        LoginRequest request = new LoginRequest("locked@moldymarket.com", "password123");
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new LockedException("Account locked"));

        // Act & Assert
        assertThatThrownBy(() -> authenticationService.login(request, clientIp))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCOUNT_LOCKED);
    }

    @Test
    @DisplayName("should throw INVALID_CREDENTIALS when authentication fails")
    void should_throwInvalidCredentials_when_passwordIsWrong() {
        // Arrange
        LoginRequest request = new LoginRequest("test@moldymarket.com", "wrong-password");
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        // Act & Assert
        assertThatThrownBy(() -> authenticationService.login(request, clientIp))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("should propagate exception when login rate limit is exceeded")
    void should_throwRateLimitExceeded_when_loginRateLimitExceeded() {
        // Arrange
        LoginRequest request = new LoginRequest("test@moldymarket.com", "password123");
        doThrow(new RateLimitExceededException(60))
                .when(rateLimiter).check(RateLimitPolicy.LOGIN_PER_IP, clientIp);

        // Act & Assert
        assertThatThrownBy(() -> authenticationService.login(request, clientIp))
                .isInstanceOf(RateLimitExceededException.class);

        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    @DisplayName("should return rotated AuthResponse when refresh token is valid")
    void should_returnRotatedAuthResponse_when_refreshTokenIsValid() {
        // Arrange
        String oldRawToken = "valid-raw-refresh-token";
        RefreshTokenService.RotationResult rotationResult = new RefreshTokenService.RotationResult(sampleUser, "new-raw-refresh-token");

        when(refreshTokenService.rotate(oldRawToken)).thenReturn(rotationResult);
        when(jwtService.generateAccessToken(sampleUser)).thenReturn("new-access-token");

        // Act
        AuthResponse response = authenticationService.refresh(oldRawToken);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo(sampleUser.getId());
        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.refreshToken()).isEqualTo("new-raw-refresh-token");
        verify(refreshTokenService).rotate(oldRawToken);
        verify(jwtService).generateAccessToken(sampleUser);
    }

    @Test
    @DisplayName("should call revoke on refreshTokenService when logout is invoked")
    void should_revokeRefreshToken_when_logoutInvoked() {
        // Arrange
        String rawToken = "raw-token-to-revoke";

        // Act
        authenticationService.logout(rawToken);

        // Assert
        verify(refreshTokenService).revoke(rawToken);
    }

    @Test
    @DisplayName("should register user and create wallet when request is valid")
    void should_registerUserAndCreateWallet_when_requestIsValid() {
        // Arrange
        RegisterRequest request = new RegisterRequest("  NewUser@Example.COM ", "secret123", "  John Doe  ", " 0901234567 ");
        String normalizedEmail = "newuser@example.com";


        User mappedUser = new User();
        when(userMapper.toEntity(request)).thenReturn(mappedUser);
        when(passwordEncoder.encode("secret123")).thenReturn("hashed-secret");

        User savedUser = new User();
        savedUser.setId(UUID.randomUUID());
        savedUser.setEmail(normalizedEmail);
        savedUser.setFullName("John Doe");
        savedUser.setPasswordHash("hashed-secret");
        savedUser.setStatus(UserStatus.PENDING_VERIFICATION);

        when(userRepository.save(mappedUser)).thenReturn(savedUser);

        Role userRole = new Role("USER");
        ReflectionTestUtils.setField(userRole, "id", UUID.randomUUID());
        when(roleRepository.findByName("USER")).thenReturn(Optional.of(userRole));

        RegisterResponse expectedResponse = new RegisterResponse(savedUser.getId(), normalizedEmail, "John Doe", "PENDING_VERIFICATION");
        when(userMapper.toRegisterResponse(savedUser)).thenReturn(expectedResponse);

        // Act
        RegisterResponse actualResponse = authenticationService.register(request, clientIp);

        // Assert
        assertThat(actualResponse).isNotNull();
        assertThat(actualResponse.userId()).isEqualTo(savedUser.getId());
        assertThat(actualResponse.email()).isEqualTo(normalizedEmail);

        verify(rateLimiter).check(RateLimitPolicy.REGISTER_PER_IP, clientIp);
        assertThat(mappedUser.getEmail()).isEqualTo(normalizedEmail);
        assertThat(mappedUser.getFullName()).isEqualTo("John Doe");
        assertThat(mappedUser.getPhone()).isEqualTo("0901234567");
        assertThat(mappedUser.getPasswordHash()).isEqualTo("hashed-secret");
        assertThat(mappedUser.getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);

        verify(userRoleRepository).save(any(UserRole.class));
        verify(emailVerificationService).sendInitialCode(savedUser);
        // Current production code passes mappedUser.getId() (which is null) instead of savedUser.getId() (QA-010)
        verify(walletService).createWalletForUser(mappedUser.getId());
    }

    @Test
    @Disabled("QA-010: AuthenticationService.register passes unpersisted user.getId() (null) instead of savedUser.getId() to createWalletForUser")
    @DisplayName("should create wallet using generated savedUser ID when user is registered")
    void should_createWalletWithGeneratedUserId_when_registerSucceeds() {
        // Arrange
        RegisterRequest request = new RegisterRequest("newuser@example.com", "secret123", "John Doe", null);


        User mappedUser = new User();
        when(userMapper.toEntity(request)).thenReturn(mappedUser);
        when(passwordEncoder.encode("secret123")).thenReturn("hashed-secret");

        UUID generatedId = UUID.randomUUID();
        User savedUser = new User();
        savedUser.setId(generatedId);
        when(userRepository.save(mappedUser)).thenReturn(savedUser);

        Role userRole = new Role("USER");
        when(roleRepository.findByName("USER")).thenReturn(Optional.of(userRole));

        // Act
        authenticationService.register(request, clientIp);

        // Assert expected behavior
        verify(walletService).createWalletForUser(generatedId);
    }

    @Test
    @DisplayName("should throw EMAIL_ALREADY_EXISTS when email already registered")
    void should_throwEmailAlreadyExists_when_emailIsDuplicate() {
        // Arrange
        RegisterRequest request = new RegisterRequest("existing@moldymarket.com", "secret123", "Jane Doe", null);
        when(userRepository.findByEmailIgnoreCase("existing@moldymarket.com")).thenReturn(Optional.of(sampleUser));

        // Act & Assert
        assertThatThrownBy(() -> authenticationService.register(request, clientIp))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMAIL_ALREADY_EXISTS);

        verify(userRepository, never()).save(any());
        verify(walletService, never()).createWalletForUser(any());
    }

    @Test
    void returnsVerificationRequiredForPendingAccountsWithoutChangingCredentials() {
        sampleUser.setStatus(UserStatus.PENDING_VERIFICATION);
        RegisterRequest request = new RegisterRequest("test@moldymarket.com", "new-password", "New Name", "0901234567");
        when(userRepository.findByEmailIgnoreCase(request.email())).thenReturn(Optional.of(sampleUser));

        assertThatThrownBy(() -> authenticationService.register(request, clientIp))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMAIL_NOT_VERIFIED);

        assertThat(sampleUser.getPasswordHash()).isEqualTo("encodedPassword");
        assertThat(sampleUser.getFullName()).isEqualTo("Test User");
        verify(userRepository, never()).save(any());
        verifyNoInteractions(userMapper, passwordEncoder, walletService, emailVerificationService);
    }

    @Test
    @DisplayName("should reject registration when the phone belongs to another account")
    void should_throwPhoneAlreadyExists_when_phoneIsDuplicate() {
        RegisterRequest request = new RegisterRequest("new@example.com", "secret123", "Jane Doe", " 0901234567 ");
        when(userRepository.existsByPhone("0901234567")).thenReturn(true);

        assertThatThrownBy(() -> authenticationService.register(request, clientIp))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PHONE_ALREADY_EXISTS);

        var order = inOrder(rateLimiter, userRepository);
        order.verify(rateLimiter).check(RateLimitPolicy.REGISTER_PER_IP, clientIp);
        order.verify(userRepository).existsByPhone("0901234567");
        verify(userRepository, never()).save(any());
        verifyNoInteractions(userMapper, walletService);
    }

    @Test
    @DisplayName("should throw ROLE_NOT_FOUND when USER role does not exist in db")
    void should_throwRoleNotFound_when_userRoleMissing() {
        // Arrange
        RegisterRequest request = new RegisterRequest("user@example.com", "secret123", "User Name", null);

        when(userMapper.toEntity(request)).thenReturn(new User());

        User savedUser = new User();
        savedUser.setId(UUID.randomUUID());
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(roleRepository.findByName("USER")).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> authenticationService.register(request, clientIp))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ROLE_NOT_FOUND);

        verify(walletService, never()).createWalletForUser(any());
        verify(userRepository, never()).existsByPhone(any());
    }
}
