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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AppraiserProfileService {

    private final AppraiserProfileRepository appraiserProfileRepository;
    private final AppraiserCategoryRepository appraiserCategoryRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final AppraiserMapper appraiserMapper;

    @Transactional
    public AppraiserProfileResponse apply(UUID userId, AppraiserApplyRequest request) {
        if (appraiserProfileRepository.existsByUserId(userId)) {
            throw new AppException(ErrorCode.APPRAISER_PROFILE_ALREADY_EXISTS);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        for (UUID categoryId : request.categoryIds()) {
            if (!categoryRepository.existsById(categoryId)) {
                throw new AppException(ErrorCode.CATEGORY_NOT_FOUND);
            }
        }

        AppraiserProfileEntity profile = new AppraiserProfileEntity(userId);
        AppraiserProfileEntity savedProfile = appraiserProfileRepository.save(profile);

        List<UUID> savedCategoryIds = new ArrayList<>();
        for (UUID categoryId : request.categoryIds()) {
            AppraiserCategoryEntity mapping = new AppraiserCategoryEntity(savedProfile.getId(), categoryId);
            appraiserCategoryRepository.save(mapping);
            savedCategoryIds.add(categoryId);
        }

        return appraiserMapper.toProfileResponse(savedProfile, user, savedCategoryIds);
    }

    @Transactional
    public AppraiserProfileResponse approve(UUID profileId, UUID adminId) {
        AppraiserProfileEntity profile = appraiserProfileRepository.findById(profileId)
                .orElseThrow(() -> new AppException(ErrorCode.APPRAISER_PROFILE_NOT_FOUND));

        profile.approve();
        AppraiserProfileEntity savedProfile = appraiserProfileRepository.save(profile);

        User user = userRepository.findById(profile.getUserId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Role appraiserRole = roleRepository.findByName(RoleConstants.APPRAISER)
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));

        if (!userRoleRepository.existsByUserIdAndRole_Name(user.getId(), RoleConstants.APPRAISER)) {
            userRoleRepository.save(new UserRole(user, appraiserRole, null));
        }

        List<UUID> categoryIds = appraiserCategoryRepository.findByAppraiserId(profile.getId())
                .stream()
                .map(AppraiserCategoryEntity::getCategoryId)
                .toList();

        return appraiserMapper.toProfileResponse(savedProfile, user, categoryIds);
    }

    @Transactional
    public AppraiserProfileResponse suspend(UUID profileId, UUID adminId) {
        AppraiserProfileEntity profile = appraiserProfileRepository.findById(profileId)
                .orElseThrow(() -> new AppException(ErrorCode.APPRAISER_PROFILE_NOT_FOUND));

        profile.suspend();
        AppraiserProfileEntity savedProfile = appraiserProfileRepository.save(profile);

        User user = userRepository.findById(profile.getUserId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        List<UUID> categoryIds = appraiserCategoryRepository.findByAppraiserId(profile.getId())
                .stream()
                .map(AppraiserCategoryEntity::getCategoryId)
                .toList();

        return appraiserMapper.toProfileResponse(savedProfile, user, categoryIds);
    }

    public AppraiserProfileResponse getMyProfile(UUID userId) {
        AppraiserProfileEntity profile = appraiserProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.APPRAISER_PROFILE_NOT_FOUND));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        List<UUID> categoryIds = appraiserCategoryRepository.findByAppraiserId(profile.getId())
                .stream()
                .map(AppraiserCategoryEntity::getCategoryId)
                .toList();

        return appraiserMapper.toProfileResponse(profile, user, categoryIds);
    }

    public AppraiserProfileResponse getProfileById(UUID profileId) {
        AppraiserProfileEntity profile = appraiserProfileRepository.findById(profileId)
                .orElseThrow(() -> new AppException(ErrorCode.APPRAISER_PROFILE_NOT_FOUND));

        User user = userRepository.findById(profile.getUserId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        List<UUID> categoryIds = appraiserCategoryRepository.findByAppraiserId(profile.getId())
                .stream()
                .map(AppraiserCategoryEntity::getCategoryId)
                .toList();

        return appraiserMapper.toProfileResponse(profile, user, categoryIds);
    }

    public Page<AppraiserProfileResponse> getAllProfiles(Pageable pageable, AppraiserStatus status) {
        Page<AppraiserProfileEntity> profiles = (status != null)
                ? appraiserProfileRepository.findByStatus(status, pageable)
                : appraiserProfileRepository.findAll(pageable);

        return profiles.map(profile -> {
            User user = userRepository.findById(profile.getUserId()).orElse(null);
            List<UUID> categoryIds = appraiserCategoryRepository.findByAppraiserId(profile.getId())
                    .stream()
                    .map(AppraiserCategoryEntity::getCategoryId)
                    .toList();
            return appraiserMapper.toProfileResponse(profile, user, categoryIds);
        });
    }

    public List<AppraiserProfileResponse> getActiveAppraisersByCategory(UUID categoryId) {
        List<AppraiserProfileEntity> activeProfiles;
        if (categoryId != null) {
            List<AppraiserCategoryEntity> mappings = appraiserCategoryRepository.findByCategoryId(categoryId);
            List<UUID> profileIds = mappings.stream().map(AppraiserCategoryEntity::getAppraiserId).toList();
            activeProfiles = appraiserProfileRepository.findAllById(profileIds).stream()
                    .filter(AppraiserProfileEntity::isApproved)
                    .toList();
        } else {
            activeProfiles = appraiserProfileRepository.findByStatus(AppraiserStatus.ACTIVE);
        }

        return activeProfiles.stream().map(profile -> {
            User user = userRepository.findById(profile.getUserId()).orElse(null);
            List<UUID> categoryIds = appraiserCategoryRepository.findByAppraiserId(profile.getId())
                    .stream()
                    .map(AppraiserCategoryEntity::getCategoryId)
                    .toList();
            return appraiserMapper.toProfileResponse(profile, user, categoryIds);
        }).toList();
    }
}
