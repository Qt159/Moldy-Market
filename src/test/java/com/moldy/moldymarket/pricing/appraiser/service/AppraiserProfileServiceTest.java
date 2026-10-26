package com.moldy.moldymarket.pricing.appraiser.service;

import com.moldy.moldymarket.category.repository.CategoryRepository;
import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.pricing.appraiser.dto.AppraiserApplyRequest;
import com.moldy.moldymarket.pricing.appraiser.dto.AppraiserProfileResponse;
import com.moldy.moldymarket.pricing.appraiser.entity.AppraiserCategoryEntity;
import com.moldy.moldymarket.pricing.appraiser.entity.AppraiserProfileEntity;
import com.moldy.moldymarket.pricing.appraiser.entity.AppraiserStatus;
import com.moldy.moldymarket.pricing.appraiser.mapper.AppraiserMapper;
import com.moldy.moldymarket.pricing.appraiser.repository.AppraiserCategoryRepository;
import com.moldy.moldymarket.pricing.appraiser.repository.AppraiserProfileRepository;
import com.moldy.moldymarket.role.entity.Role;
import com.moldy.moldymarket.role.repository.RoleRepository;
import com.moldy.moldymarket.security.RoleConstants;
import com.moldy.moldymarket.user.entity.User;
import com.moldy.moldymarket.user.repository.UserRepository;
import com.moldy.moldymarket.userrole.entity.UserRole;
import com.moldy.moldymarket.userrole.repository.UserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppraiserProfileServiceTest {

    @Mock
    private AppraiserProfileRepository appraiserProfileRepository;

    @Mock
    private AppraiserCategoryRepository appraiserCategoryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Spy
    private AppraiserMapper appraiserMapper = new AppraiserMapper();

    @InjectMocks
    private AppraiserProfileService appraiserProfileService;

    private UUID userId;
    private UUID categoryId;
    private User mockUser;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        categoryId = UUID.randomUUID();

        mockUser = new User();
        mockUser.setId(userId);
        mockUser.setFullName("Nguyen Van A");
        mockUser.setEmail("appraiser@example.com");
    }

    @Test
    @DisplayName("Nộp hồ sơ thẩm định viên thành công với trạng thái PENDING")
    void apply_Success() {
        AppraiserApplyRequest request = new AppraiserApplyRequest(List.of(categoryId));

        when(appraiserProfileRepository.existsByUserId(userId)).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        when(categoryRepository.existsById(categoryId)).thenReturn(true);

        AppraiserProfileEntity savedEntity = new AppraiserProfileEntity(userId);
        when(appraiserProfileRepository.save(any(AppraiserProfileEntity.class))).thenReturn(savedEntity);

        AppraiserProfileResponse response = appraiserProfileService.apply(userId, request);

        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.status()).isEqualTo(AppraiserStatus.PENDING);
        assertThat(response.categoryIds()).containsExactly(categoryId);
        verify(appraiserCategoryRepository).save(any(AppraiserCategoryEntity.class));
    }

    @Test
    @DisplayName("Nộp hồ sơ khi đã có hồ sơ thì ném lỗi APPRAISER_PROFILE_ALREADY_EXISTS")
    void apply_AlreadyExists_ThrowsException() {
        AppraiserApplyRequest request = new AppraiserApplyRequest(List.of(categoryId));
        when(appraiserProfileRepository.existsByUserId(userId)).thenReturn(true);

        assertThatThrownBy(() -> appraiserProfileService.apply(userId, request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.APPRAISER_PROFILE_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("Nộp hồ sơ với danh mục không tồn tại thì ném lỗi CATEGORY_NOT_FOUND")
    void apply_CategoryNotFound_ThrowsException() {
        AppraiserApplyRequest request = new AppraiserApplyRequest(List.of(categoryId));
        when(appraiserProfileRepository.existsByUserId(userId)).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        when(categoryRepository.existsById(categoryId)).thenReturn(false);

        assertThatThrownBy(() -> appraiserProfileService.apply(userId, request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CATEGORY_NOT_FOUND);
    }

    @Test
    @DisplayName("Admin duyệt hồ sơ thành công, cấp quyền APPRAISER")
    void approve_Success() {
        UUID profileId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        AppraiserProfileEntity profile = new AppraiserProfileEntity(userId);
        when(appraiserProfileRepository.findById(profileId)).thenReturn(Optional.of(profile));
        when(appraiserProfileRepository.save(profile)).thenReturn(profile);
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));

        Role mockRole = new Role(RoleConstants.APPRAISER);
        when(roleRepository.findByName(RoleConstants.APPRAISER)).thenReturn(Optional.of(mockRole));
        when(userRoleRepository.existsByUserIdAndRole_Name(userId, RoleConstants.APPRAISER)).thenReturn(false);
        when(appraiserCategoryRepository.findByAppraiserId(profile.getId())).thenReturn(List.of(
                new AppraiserCategoryEntity(profile.getId(), categoryId)
        ));

        AppraiserProfileResponse response = appraiserProfileService.approve(profileId, adminId);

        assertThat(response.status()).isEqualTo(AppraiserStatus.ACTIVE);
        verify(userRoleRepository).save(any(UserRole.class));
    }

    @Test
    @DisplayName("Admin đình chỉ thẩm định viên thành công")
    void suspend_Success() {
        UUID profileId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        AppraiserProfileEntity profile = new AppraiserProfileEntity(userId);
        profile.approve();

        when(appraiserProfileRepository.findById(profileId)).thenReturn(Optional.of(profile));
        when(appraiserProfileRepository.save(profile)).thenReturn(profile);
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        when(appraiserCategoryRepository.findByAppraiserId(profile.getId())).thenReturn(List.of());

        AppraiserProfileResponse response = appraiserProfileService.suspend(profileId, adminId);

        assertThat(response.status()).isEqualTo(AppraiserStatus.SUSPENDED);
    }

    @Test
    @DisplayName("Lấy hồ sơ cá nhân thành công")
    void getMyProfile_Success() {
        AppraiserProfileEntity profile = new AppraiserProfileEntity(userId);
        when(appraiserProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        when(appraiserCategoryRepository.findByAppraiserId(profile.getId())).thenReturn(List.of(
                new AppraiserCategoryEntity(profile.getId(), categoryId)
        ));

        AppraiserProfileResponse response = appraiserProfileService.getMyProfile(userId);

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.fullName()).isEqualTo("Nguyen Van A");
        assertThat(response.categoryIds()).containsExactly(categoryId);
    }
}
