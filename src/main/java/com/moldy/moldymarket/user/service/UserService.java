package com.moldy.moldymarket.user.service;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.common.response.PagedResponse;
import com.moldy.moldymarket.common.storage.entity.StoredFile;
import com.moldy.moldymarket.common.storage.enums.FilePurpose;
import com.moldy.moldymarket.common.storage.enums.FileStatus;
import com.moldy.moldymarket.common.storage.repository.StoredFileRepository;
import com.moldy.moldymarket.security.user.CustomUserDetails;
import com.moldy.moldymarket.user.dto.request.ChangePasswordRequest;
import com.moldy.moldymarket.user.dto.request.LockUserRequest;
import com.moldy.moldymarket.user.dto.request.UpdateProfileRequest;
import com.moldy.moldymarket.user.dto.request.UpdateUserStatusRequest;
import com.moldy.moldymarket.user.dto.response.UserProfileReSponse;
import com.moldy.moldymarket.user.dto.response.UserSummaryResponse;
import com.moldy.moldymarket.user.entity.User;
import com.moldy.moldymarket.user.entity.UserStatus;
import com.moldy.moldymarket.user.mapper.UserMapper;
import com.moldy.moldymarket.user.repository.UserRepository;
import com.moldy.moldymarket.userrole.entity.UserRole;
import com.moldy.moldymarket.userrole.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final StoredFileRepository storedFileRepository;

    // Lấy thông tin user đang login
    @Transactional
    public UserProfileReSponse getMyProfile(Authentication authentication) {
        User user = extractUser(authentication);
        return buildMyProfile(user, authentication);
    }

    // Update profile
    @Transactional
    public UserProfileReSponse updateMyProfile(Authentication authentication, UpdateProfileRequest request) {
        User user = extractUser(authentication);

        user.setFullName(request.fullName().trim());

        if (request.phone() != null && !request.phone().isBlank()) {
            userRepository.findByPhone(request.phone())
                    .filter(existing -> !existing.getId().equals(user.getId()))
                    .ifPresent(__ -> {
                        throw new AppException(ErrorCode.PHONE_ALREADY_EXISTS);
                    });
            user.setPhone(request.phone());
        }

        if (request.avatarFileId() != null) {
            StoredFile file = storedFileRepository.findById(request.avatarFileId())
                    .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
            if (!file.getOwnerId().equals(user.getId())) {
                throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
            }
            if (file.getPurpose() != FilePurpose.USER_AVATAR || file.getStatus() != FileStatus.AVAILABLE) {
                throw new AppException(ErrorCode.FILE_NOT_AVAILABLE);
            }
            unlinkCurrentAvatar(user);
            user.setAvatarFileId(file.getId());
            user.setAvatarUrl(null);
            file.setLinkedAt(LocalDateTime.now());
            storedFileRepository.save(file);
        } else if (request.avatarUrl() != null) {
            String trimmedAvatar = request.avatarUrl().trim();
            if (trimmedAvatar.matches("(?i).*X-Amz-(Signature|Credential|Algorithm)=.*")) {
                throw new AppException(ErrorCode.INVALID_REQUEST, "Presigned storage URLs cannot be stored as avatar URLs");
            }
            unlinkCurrentAvatar(user);
            user.setAvatarUrl(trimmedAvatar.isEmpty() ? null : trimmedAvatar);
            user.setAvatarFileId(null);
        }

        if (request.address() != null) {
            String trimmedAddress = request.address().trim();
            user.setAddress(trimmedAddress.isEmpty() ? null : trimmedAddress);
        }

        userRepository.save(user);
        return buildMyProfile(user, authentication);
    }

    private void unlinkCurrentAvatar(User user) {
        if (user.getAvatarFileId() == null) return;
        storedFileRepository.findById(user.getAvatarFileId()).ifPresent(file -> {
            file.setLinkedAt(null);
            storedFileRepository.save(file);
        });
        user.setAvatarFileId(null);
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
        Slice<User> slice = userRepository.findAll(pageable);
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

    private UserProfileReSponse buildMyProfile(User user, Authentication authentication) {
        List<UserRole> userRoles = userRoleRepository.findAllByUserId(user.getId());

        List<String> roles = userRoles.stream()
                .map(ur -> ur.getRole().getName())
                .toList();

        UUID storeId = userRoles.stream()
                .map(UserRole::getStoreId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        List<String> permissions = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> !a.startsWith("ROLE_"))
                .distinct()
                .sorted()
                .toList();

        UserProfileReSponse response = userMapper.toProfileResponse(user, roles, permissions, storeId);
        String avatarUrl = user.getAvatarFileId() == null ? response.avatarUrl()
                : "/api/v1/files/" + user.getAvatarFileId() + "/view";
        return new UserProfileReSponse(response.id(), response.email(), response.fullName(), response.phone(),
                avatarUrl, response.address(), response.status(), response.trustPoints(), response.roles(),
                response.permissions(), response.storeId(), response.createdAt());
    }
}

