package com.moldy.moldymarket.user.service;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.role.entity.Role;
import com.moldy.moldymarket.role.repository.RoleRepository;
import com.moldy.moldymarket.security.jwt.JwtService;
import com.moldy.moldymarket.security.refreshToken.service.RefreshTokenService;
import com.moldy.moldymarket.security.user.CustomUserDetails;
import com.moldy.moldymarket.user.dto.AuthResponse;
import com.moldy.moldymarket.user.dto.LoginRequest;
import com.moldy.moldymarket.user.dto.RegisterRequest;
import com.moldy.moldymarket.user.dto.RegisterResponse;
import com.moldy.moldymarket.user.entity.User;
import com.moldy.moldymarket.user.entity.UserStatus;
import com.moldy.moldymarket.user.mapper.UserMapper;
import com.moldy.moldymarket.user.repository.UserRepository;
import com.moldy.moldymarket.userrole.entity.UserRole;
import com.moldy.moldymarket.userrole.repository.UserRoleRepository;
import lombok.AllArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@AllArgsConstructor
public class AuthenticationService {
    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final RefreshTokenService refreshTokenService;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;

    @Transactional
    public AuthResponse login(LoginRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.email(),
                            request.password()
                    )
            );
        } catch (DisabledException e) {
            throw new AppException(ErrorCode.ACCOUNT_DISABLED);
        } catch (LockedException e) {
            throw new AppException(ErrorCode.ACCOUNT_LOCKED);
        } catch (AuthenticationException e) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        User user = userDetails.getUser();

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = refreshTokenService.issueToken(user);

        return new AuthResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                accessToken,
                refreshToken,
                "Bearer"
        );
    }

    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        RefreshTokenService.RotationResult result = refreshTokenService.rotate(rawRefreshToken);
        User user = result.user();

        String newAccessToken = jwtService.generateAccessToken(user);

        return new AuthResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                newAccessToken,
                result.newRawToken(),
                "Bearer"
        );
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new AppException(
                    ErrorCode.EMAIL_ALREADY_EXISTS,
                    "Email is already registered");
        }
        User user = userMapper.toEntity(request);

        user.setEmail(normalizedEmail);
        user.setFullName(request.fullName().trim());
        user.setPasswordHash(
                passwordEncoder.encode(request.password())
        );
        user.setStatus(UserStatus.PENDING_VERIFICATION);

        User savedUser = userRepository.save(user);

        Role userRole = roleRepository.findByName("USER")
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));
        userRoleRepository.save(new UserRole(savedUser, userRole, null));

        return userMapper.toRegisterResponse(savedUser);
    }

}
