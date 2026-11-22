package com.moldy.moldymarket.user.service;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.common.response.PagedResponse;
import com.moldy.moldymarket.role.entity.Role;
import com.moldy.moldymarket.security.user.CustomUserDetails;
import com.moldy.moldymarket.user.dto.request.ChangePasswordRequest;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserService userService;

    private User sampleUser;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        sampleUser = new User();
        sampleUser.setId(userId);
        sampleUser.setEmail("user@example.com");
        sampleUser.setFullName("Original Name");
        sampleUser.setPhone("0901234567");
        sampleUser.setPasswordHash("hashedOldPassword");
        sampleUser.setStatus(UserStatus.ACTIVE);
        sampleUser.setCreatedAt(Instant.now());
    }

    private Authentication mockAuth(UUID uid) {
        Authentication auth = mock(Authentication.class);
        CustomUserDetails userDetails = mock(CustomUserDetails.class);
        when(userDetails.getUserId()).thenReturn(uid);
        when(auth.getPrincipal()).thenReturn(userDetails);
        return auth;
    }

    @Test
    @DisplayName("should return my profile when authentication is valid")
    void should_returnMyProfile_when_authenticated() {
        // Arrange
        Authentication auth = mockAuth(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));

        Role role = new Role("USER");
        ReflectionTestUtils.setField(role, "id", UUID.randomUUID());
        UserRole userRole = new UserRole(sampleUser, role, null);
        when(userRoleRepository.findAllByUserId(userId)).thenReturn(List.of(userRole));

        doReturn(List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("PRODUCT:READ")
        )).when(auth).getAuthorities();

        UserProfileReSponse expectedResponse = new UserProfileReSponse(
                userId, "user@example.com", "Original Name", "0901234567", null,
                UserStatus.ACTIVE, 0, List.of("USER"), List.of("PRODUCT:READ"), null, sampleUser.getCreatedAt()
        );
        when(userMapper.toProfileResponse(eq(sampleUser), eq(List.of("USER")), eq(List.of("PRODUCT:READ")), isNull()))
                .thenReturn(expectedResponse);

        // Act
        UserProfileReSponse result = userService.getMyProfile(auth);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(userId);
        assertThat(result.email()).isEqualTo("user@example.com");
    }

    @Test
    @DisplayName("should update profile successfully when phone is not duplicate")
    void should_updateMyProfile_when_requestIsValid() {
        // Arrange
        Authentication auth = mockAuth(userId);
        UpdateProfileRequest request = new UpdateProfileRequest("  Updated Name  ", "0987654321");

        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
        when(userRepository.findByPhone("0987654321")).thenReturn(Optional.empty());
        when(userRoleRepository.findAllByUserId(userId)).thenReturn(Collections.emptyList());
        doReturn(Collections.emptyList()).when(auth).getAuthorities();

        UserProfileReSponse expectedResponse = new UserProfileReSponse(
                userId, "user@example.com", "Updated Name", "0987654321", null,
                UserStatus.ACTIVE, 0, Collections.emptyList(), Collections.emptyList(), null, sampleUser.getCreatedAt()
        );
        when(userMapper.toProfileResponse(any(User.class), anyList(), anyList(), any()))
                .thenReturn(expectedResponse);

        // Act
        UserProfileReSponse result = userService.updateMyProfile(auth, request);

        // Assert
        assertThat(result).isNotNull();
        assertThat(sampleUser.getFullName()).isEqualTo("Updated Name");
        assertThat(sampleUser.getPhone()).isEqualTo("0987654321");
        verify(userRepository).save(sampleUser);
    }

    @Test
    @DisplayName("should throw PHONE_ALREADY_EXISTS when phone belongs to another user")
    void should_throwPhoneAlreadyExists_when_phoneBelongsToAnother() {
        // Arrange
        Authentication auth = mockAuth(userId);
        UpdateProfileRequest request = new UpdateProfileRequest("New Name", "0999999999");

        User otherUser = new User();
        otherUser.setId(UUID.randomUUID());
        otherUser.setPhone("0999999999");

        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
        when(userRepository.findByPhone("0999999999")).thenReturn(Optional.of(otherUser));

        // Act & Assert
        assertThatThrownBy(() -> userService.updateMyProfile(auth, request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PHONE_ALREADY_EXISTS);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("should change password when old password matches and new password is different")
    void should_changePassword_when_currentPasswordMatches() {
        // Arrange
        Authentication auth = mockAuth(userId);
        ChangePasswordRequest request = new ChangePasswordRequest("OldPass123!", "NewPass456!");

        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("OldPass123!", "hashedOldPassword")).thenReturn(true);
        when(passwordEncoder.encode("NewPass456!")).thenReturn("hashedNewPassword");

        // Act
        userService.changePassword(auth, request);

        // Assert
        assertThat(sampleUser.getPasswordHash()).isEqualTo("hashedNewPassword");
        verify(userRepository).save(sampleUser);
    }

    @Test
    @DisplayName("should throw INVALID_CREDENTIALS when current password does not match")
    void should_throwInvalidCredentials_when_currentPasswordDoesNotMatch() {
        // Arrange
        Authentication auth = mockAuth(userId);
        ChangePasswordRequest request = new ChangePasswordRequest("WrongPass!", "NewPass456!");

        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("WrongPass!", "hashedOldPassword")).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> userService.changePassword(auth, request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_CREDENTIALS);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("should throw SAME_PASSWORD when new password is identical to current password")
    void should_throwSamePassword_when_newPasswordEqualsCurrent() {
        // Arrange
        Authentication auth = mockAuth(userId);
        ChangePasswordRequest request = new ChangePasswordRequest("SamePass123!", "SamePass123!");

        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("SamePass123!", "hashedOldPassword")).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> userService.changePassword(auth, request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SAME_PASSWORD);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("should return UserProfileResponse when user exists by ID")
    void should_returnUserProfile_when_userExistsById() {
        // Arrange
        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
        Role role = new Role("ADMIN");
        ReflectionTestUtils.setField(role, "id", UUID.randomUUID());
        when(userRoleRepository.findAllByUserId(userId)).thenReturn(List.of(new UserRole(sampleUser, role, null)));

        UserProfileReSponse expectedResponse = new UserProfileReSponse(
                userId, "user@example.com", "Original Name", "0901234567", null,
                UserStatus.ACTIVE, 0, List.of("ADMIN"), Collections.emptyList(), null, sampleUser.getCreatedAt()
        );
        when(userMapper.toProfileResponse(sampleUser, List.of("ADMIN"))).thenReturn(expectedResponse);

        // Act
        UserProfileReSponse response = userService.getUserById(userId);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(userId);
    }

    @Test
    @DisplayName("should throw USER_NOT_FOUND when user does not exist by ID")
    void should_throwUserNotFound_when_userDoesNotExistById() {
        // Arrange
        UUID unknownId = UUID.randomUUID();
        when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> userService.getUserById(unknownId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("should update user status when user exists")
    void should_updateUserStatus_when_userExists() {
        // Arrange
        UpdateUserStatusRequest request = new UpdateUserStatusRequest(UserStatus.LOCKED);
        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
        when(userRoleRepository.findAllByUserId(userId)).thenReturn(Collections.emptyList());

        UserProfileReSponse expectedResponse = new UserProfileReSponse(
                userId, "user@example.com", "Original Name", "0901234567", null,
                UserStatus.LOCKED, 0, Collections.emptyList(), Collections.emptyList(), null, sampleUser.getCreatedAt()
        );
        when(userMapper.toProfileResponse(sampleUser, Collections.emptyList())).thenReturn(expectedResponse);

        // Act
        UserProfileReSponse response = userService.updateUserStatus(userId, request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(sampleUser.getStatus()).isEqualTo(UserStatus.LOCKED);
        verify(userRepository).save(sampleUser);
    }

    @Test
    @DisplayName("should return paged users")
    void should_returnPagedUsers_when_getAllUsersInvoked() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> page = new PageImpl<>(List.of(sampleUser), pageable, 1);
        when(userRepository.findAll(pageable)).thenReturn(page);

        UserSummaryResponse summaryResponse = new UserSummaryResponse(
                userId, "user@example.com", "Original Name",
                UserStatus.ACTIVE, 0, sampleUser.getCreatedAt()
        );
        when(userMapper.toSummaryResponse(sampleUser)).thenReturn(summaryResponse);

        // Act
        PagedResponse<UserSummaryResponse> response = userService.getAllUsers(pageable);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).id()).isEqualTo(userId);
    }
}
