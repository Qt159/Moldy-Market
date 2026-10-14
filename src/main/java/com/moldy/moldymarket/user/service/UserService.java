package com.moldy.moldymarket.user.service;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.common.response.PagedResponse;
import com.moldy.moldymarket.security.user.CustomUserDetails;
import com.moldy.moldymarket.user.dto.request.ChangePasswordRequest;
import com.moldy.moldymarket.user.dto.request.UpdateProfileRequest;
import com.moldy.moldymarket.user.dto.request.UpdateUserStatusRequest;
import com.moldy.moldymarket.user.dto.response.UserProfileReSponse;
import com.moldy.moldymarket.user.dto.response.UserSummaryResponse;
import com.moldy.moldymarket.user.entity.User;
import com.moldy.moldymarket.user.mapper.UserMapper;
import com.moldy.moldymarket.user.repository.UserRepository;
import com.moldy.moldymarket.userrole.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    // Lấy thông tin user đang login
    @Transactional
    public UserProfileReSponse getMyProfile(Authentication authentication) {
        User user = extractUser(authentication);
        List<String> roles = loadRoleNames(user.getId());
        return userMapper.toProfileResponse(user, roles);
    }

    // Update profile
    @Transactional
    public UserProfileReSponse updateMyProfile(Authentication authentication, UpdateProfileRequest request) {
        User user = extractUser(authentication);

        if (request.phone() != null && !request.phone().isBlank()) {
            userRepository.findByPhone(request.phone())
                    .filter(existing -> existing.getId().equals(user.getId()))
                    .ifPresent(__ -> {
                        throw new AppException(ErrorCode.PHONE_ALREADY_EXISTS);
                    });
            user.setPhone(request.phone());
        }

        userRepository.save(user);

        return userMapper.toProfileResponse(user, loadRoleNames(user.getId()));
    }

    // Change password
    @Transactional
    public void changePassword(Authentication authentication, ChangePasswordRequest request) {
        User user = extractUser(authentication);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }

        if (request.currentPassword().equals(request.newPassword())) {
            throw new AppException(ErrorCode.SAME_PASSWORD);
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    // Admin: xem user
    public UserProfileReSponse getUserById(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        return userMapper.toProfileResponse(user, loadRoleNames(userId));
    }

    // Admin: xem toàn bộ user
    public PagedResponse<UserSummaryResponse> getAllUsers(Pageable pageable) {
        Slice<User> slice = userRepository.findAllBy(pageable);
        List<UserSummaryResponse> content = slice.stream()
                .map(userMapper::toSummaryResponse)
                .toList();

        return PagedResponse.of(slice, content, pageable);
    }

    @Transactional
    public UserProfileReSponse updateUserStatus(UUID userId, UpdateUserStatusRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        user.setStatus(request.status());
        userRepository.save(user);

        return userMapper.toProfileResponse(user, loadRoleNames(userId));
    }

    private User extractUser(Authentication authentication) {
        CustomUserDetails principal = (CustomUserDetails) authentication.getPrincipal();
        return userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    private List<String> loadRoleNames(UUID userId) {
        return userRoleRepository.findAllByUserId(userId)
                .stream()
                .map(ur -> ur.getRole().getName())
                .toList();
    }
}

