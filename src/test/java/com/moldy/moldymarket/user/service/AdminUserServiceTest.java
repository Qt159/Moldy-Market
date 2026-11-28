package com.moldy.moldymarket.user.service;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.config.CurrentUser;
import com.moldy.moldymarket.order.repository.OrderRepository;
import com.moldy.moldymarket.role.entity.Role;
import com.moldy.moldymarket.security.RoleConstants;
import com.moldy.moldymarket.security.refreshToken.service.RefreshTokenService;
import com.moldy.moldymarket.store.repository.StoreRepository;
import com.moldy.moldymarket.user.dto.request.LockUserRequest;
import com.moldy.moldymarket.user.dto.request.UnlockUserRequest;
import com.moldy.moldymarket.user.dto.response.AdminUserDetailResponse;
import com.moldy.moldymarket.user.entity.User;
import com.moldy.moldymarket.user.entity.UserStatus;
import com.moldy.moldymarket.user.entity.UserStatusHistory;
import com.moldy.moldymarket.user.event.UserAccountStatusChangedEvent;
import com.moldy.moldymarket.user.repository.UserRepository;
import com.moldy.moldymarket.user.repository.UserStatusHistoryRepository;
import com.moldy.moldymarket.userrole.entity.UserRole;
import com.moldy.moldymarket.userrole.repository.UserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminUserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private UserStatusHistoryRepository historyRepository;
    @Mock private StoreRepository storeRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private CurrentUser currentUser;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private AdminUserService adminUserService;

    private User admin;
    private User target;

    @BeforeEach
    void setUp() {
        admin = user(UserStatus.ACTIVE);
        target = user(UserStatus.ACTIVE);

        when(currentUser.findCurrentUser()).thenReturn(admin);
        when(userRepository.findByIdForUpdate(target.getId())).thenReturn(Optional.of(target));
        when(userRepository.findByIdForUpdate(admin.getId())).thenReturn(Optional.of(admin));
        when(userRoleRepository.findAllWithRoleByUserIdIn(anyCollection())).thenReturn(List.of());
        when(storeRepository.findByOwnerId(any())).thenReturn(Optional.empty());
        when(historyRepository.save(any(UserStatusHistory.class))).thenAnswer(inv -> {
            UserStatusHistory h = inv.getArgument(0);
            ReflectionTestUtils.setField(h, "id", UUID.randomUUID());
            return h;
        });
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of(admin));
    }

    @Test
    @DisplayName("khóa user ACTIVE: chuyển LOCKED, lưu lý do, thu hồi refresh token, ghi lịch sử, phát event")
    void lock_activeUser_success() {
        AdminUserDetailResponse res = adminUserService.lockUser(target.getId(), new LockUserRequest("Spam đăng 50 bài giống nhau"));

        assertThat(target.getStatus()).isEqualTo(UserStatus.LOCKED);
        assertThat(target.getLockReason()).isEqualTo("Spam đăng 50 bài giống nhau");
        assertThat(target.getLockedBy()).isEqualTo(admin.getId());
        assertThat(target.getLockedAt()).isNotNull();
        assertThat(res.lock()).isNotNull();
        assertThat(res.canLock()).isFalse();
        assertThat(res.canUnlock()).isTrue();

        verify(refreshTokenService).revokeAllForUser(target);

        ArgumentCaptor<UserStatusHistory> history = ArgumentCaptor.forClass(UserStatusHistory.class);
        verify(historyRepository).save(history.capture());
        assertThat(history.getValue().getFromStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(history.getValue().getToStatus()).isEqualTo(UserStatus.LOCKED);
        assertThat(history.getValue().getActorId()).isEqualTo(admin.getId());

        verify(eventPublisher).publishEvent(any(UserAccountStatusChangedEvent.class));
    }

    @Test
    @DisplayName("không được tự khóa chính mình")
    void lock_self_rejected() {
        assertError(() -> adminUserService.lockUser(admin.getId(), new LockUserRequest("Lý do đủ mười ký tự")),
                ErrorCode.CANNOT_LOCK_YOURSELF);
        assertThat(admin.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("không được khóa tài khoản ADMIN khác")
    void lock_admin_rejected() {
        UserRole adminRole = mock(UserRole.class);
        Role role = mock(Role.class);
        when(role.getName()).thenReturn(RoleConstants.ADMIN);
        when(adminRole.getRole()).thenReturn(role);
        when(adminRole.getUser()).thenReturn(target);
        when(userRoleRepository.findAllWithRoleByUserIdIn(anyCollection())).thenReturn(List.of(adminRole));

        assertError(() -> adminUserService.lockUser(target.getId(), new LockUserRequest("Lý do đủ mười ký tự")),
                ErrorCode.USER_CANNOT_LOCK_ADMIN);
    }

    @Test
    @DisplayName("không được khóa user đang chờ xác minh OTP")
    void lock_pendingVerification_rejected() {
        target.setStatus(UserStatus.PENDING_VERIFICATION);
        assertError(() -> adminUserService.lockUser(target.getId(), new LockUserRequest("Lý do đủ mười ký tự")),
                ErrorCode.USER_PENDING_VERIFICATION);
    }

    @Test
    @DisplayName("khóa user đã bị khóa → lỗi, không ghi lịch sử")
    void lock_alreadyLocked_rejected() {
        target.setStatus(UserStatus.LOCKED);
        assertError(() -> adminUserService.lockUser(target.getId(), new LockUserRequest("Lý do đủ mười ký tự")),
                ErrorCode.USER_ALREADY_LOCKED);
        verify(historyRepository, never()).save(any());
        verify(refreshTokenService, never()).revokeAllForUser(any());
    }

    @Test
    @DisplayName("mở khóa user LOCKED: về ACTIVE và xóa thông tin khóa")
    void unlock_lockedUser_success() {
        target.lock(admin.getId(), "Spam", java.time.Instant.now());

        adminUserService.unlockUser(target.getId(), new UnlockUserRequest("Đã xác minh lại"));

        assertThat(target.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(target.getLockReason()).isNull();
        assertThat(target.getLockedBy()).isNull();
        verify(eventPublisher).publishEvent(any(UserAccountStatusChangedEvent.class));
    }

    @Test
    @DisplayName("mở khóa user đang ACTIVE → lỗi")
    void unlock_activeUser_rejected() {
        assertError(() -> adminUserService.unlockUser(target.getId(), null), ErrorCode.USER_NOT_LOCKED);
    }

    @Test
    @DisplayName("mở khóa user PENDING_VERIFICATION → lỗi (không bypass OTP)")
    void unlock_pendingVerification_rejected() {
        target.setStatus(UserStatus.PENDING_VERIFICATION);
        assertError(() -> adminUserService.unlockUser(target.getId(), null), ErrorCode.USER_PENDING_VERIFICATION);
        assertThat(target.getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);
    }

    @Test
    @DisplayName("API cũ: PENDING_VERIFICATION không được chấp nhận làm trạng thái đích")
    void legacy_pendingTarget_rejected() {
        assertError(() -> adminUserService.changeStatusLegacy(target.getId(), UserStatus.PENDING_VERIFICATION),
                ErrorCode.USER_STATUS_TRANSITION_INVALID);
    }

    @Test
    @DisplayName("user không tồn tại → USER_NOT_FOUND")
    void lock_unknownUser_notFound() {
        UUID unknown = UUID.randomUUID();
        when(userRepository.findByIdForUpdate(unknown)).thenReturn(Optional.empty());
        assertError(() -> adminUserService.lockUser(unknown, new LockUserRequest("Lý do đủ mười ký tự")), ErrorCode.USER_NOT_FOUND);
    }

    // ---------- helpers ----------

    private static User user(UserStatus status) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setEmail(u.getId() + "@example.com");
        u.setFullName("User " + u.getId().toString().substring(0, 4));
        u.setStatus(status);
        return u;
    }

    private static void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call)
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", code);
    }
}