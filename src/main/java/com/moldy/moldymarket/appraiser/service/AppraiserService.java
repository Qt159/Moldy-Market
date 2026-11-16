package com.moldy.moldymarket.appraiser.service;
import com.moldy.moldymarket.appraiser.dto.request.RegisterAppraiserRequest;
import com.moldy.moldymarket.appraiser.dto.request.UpdateAppraiserProfileRequest;
import com.moldy.moldymarket.appraiser.dto.request.RejectAppraiserRequest;
import com.moldy.moldymarket.appraiser.dto.request.SuspendAppraiserRequest;
import com.moldy.moldymarket.appraiser.dto.response.AppraiserProfileResponse;
import com.moldy.moldymarket.appraiser.dto.response.AppraiserPublicProfileResponse;
import com.moldy.moldymarket.appraiser.dto.response.AppraiserRegistrationResponse;
import com.moldy.moldymarket.appraiser.dto.response.AppraiserStatusResponse;
import com.moldy.moldymarket.appraiser.entity.Appraiser;
import com.moldy.moldymarket.appraiser.entity.AppraiserCategory;
import com.moldy.moldymarket.appraiser.enums.AppraiserStatus;
import com.moldy.moldymarket.appraiser.helper.AppraiserHelper;
import com.moldy.moldymarket.appraiser.mapper.AppraiserMapper;
import com.moldy.moldymarket.appraiser.repository.AppraiserCategoryRepository;
import com.moldy.moldymarket.appraiser.repository.AppraiserRepository;
import com.moldy.moldymarket.category.entity.Category;
import com.moldy.moldymarket.category.repository.CategoryRepository;
import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AppraiserService {
    private final AppraiserRepository appraiserRepository;
    private final AppraiserCategoryRepository appraiserCategoryRepository;
    private final CategoryRepository categoryRepository;
    private final AppraiserHelper appraiserHelper;
    private final AppraiserMapper appraiserMapper;
    public AppraiserRegistrationResponse register(
            UUID userId, RegisterAppraiserRequest request) {
        log.info("Registering new appraiser for user: {}", userId);
        appraiserHelper.getUser(userId);
        if (appraiserRepository.existsByUserId(userId)) {
            throw new AppException(ErrorCode.APPRAISER_ALREADY_REGISTERED);}
        validateCategories(request.getCategoryIds());
        Appraiser appraiser = new Appraiser(userId);
        appraiser.updateProfile(request.getBio(), request.getAvatarUrl());
        appraiser = appraiserRepository.save(appraiser);
        appraiserHelper.saveCategories(appraiser.getId(), request.getCategoryIds());
        log.info("Appraiser registered successfully: {}", appraiser.getId());
        return appraiserMapper.toRegistrationResponse(appraiser);
    }
    @Transactional(readOnly = true)
    public AppraiserProfileResponse getMyProfile(UUID userId) {
        log.debug("Fetching appraiser profile for user: {}", userId);
        User user = appraiserHelper.getUser(userId);
        Appraiser appraiser = appraiserHelper.getAppraiserByUserId(userId);
        return buildProfileResponse(appraiser, user);
    }
    public AppraiserProfileResponse updateProfile(
            UUID userId, UpdateAppraiserProfileRequest request) {
        log.info("Updating appraiser profile for user: {}", userId);
        User user = appraiserHelper.getUser(userId);
        Appraiser appraiser = appraiserHelper.getAppraiserByUserId(userId);
        if (appraiser.getStatus() == AppraiserStatus.REJECTED) {
            throw new AppException(
                    ErrorCode.APPRAISER_STATUS_INVALID_FOR_UPDATE,
                    "Rejected appraisers cannot update their profiles");}
        appraiser.updateProfile(request.getBio(), request.getAvatarUrl());
        if (request.getCategoryIds() != null) {
            updateCategories(appraiser.getId(), request.getCategoryIds());
        }
        appraiser = appraiserRepository.save(appraiser);
        log.info("Appraiser profile updated successfully: {}", appraiser.getId());
        return buildProfileResponse(appraiser, user);
    }
    @Transactional(readOnly = true)
    public AppraiserPublicProfileResponse getPublicProfile(UUID appraiserId) {
        log.debug("Fetching public profile for appraiser: {}", appraiserId);
        Appraiser appraiser = appraiserHelper.getAppraiser(appraiserId);
        if (appraiser.getStatus() != AppraiserStatus.ACTIVE) {
            throw new AppException(
                    ErrorCode.APPRAISER_PROFILE_NOT_PUBLICLY_AVAILABLE);}
        User user = appraiserHelper.getUser(appraiser.getUserId());
        List<AppraiserCategory> categories =
                appraiserCategoryRepository.findByAppraiserId(appraiserId);
        Map<UUID, Category> categoryMap = loadCategories(getCategoryIds(categories));
        return appraiserMapper.toPublicProfileResponse(appraiser, user, categories, categoryMap);
    }
    @Transactional(readOnly = true)
    public AppraiserStatusResponse getStatusByUserId(UUID userId) {
        log.debug("Fetching appraiser status for user: {}", userId);
        Appraiser appraiser = appraiserHelper.getAppraiserByUserId(userId);
        return appraiserMapper.toStatusResponse(appraiser);
    }
    @Transactional(readOnly = true)
    public Page<AppraiserPublicProfileResponse> getActiveAppraisers(
            UUID categoryId, Pageable pageable) {
        log.debug("Fetching active appraisers with categoryId: {}", categoryId);
        Page<Appraiser> appraisers;
        if (categoryId != null) {
            validateCategoryExists(categoryId);
            List<UUID> appraiserIds = getAppraiserIdsByCategory(categoryId);
            if (appraiserIds.isEmpty()) {
                return Page.empty(pageable);}
            appraisers = appraiserRepository.findByStatusAndIdIn(
                    AppraiserStatus.ACTIVE, appraiserIds, pageable);
        } else {
            appraisers = appraiserRepository.findByStatusOrderByRating(
                    AppraiserStatus.ACTIVE, pageable);
        }
        return appraisers.map(this::buildPublicProfileResponse);
    }
    @Transactional(readOnly = true)
    public Page<AppraiserProfileResponse> getAppraisers(
            AppraiserStatus status, Pageable pageable) {
        log.debug("Fetching appraisers with status: {}", status);
        Page<Appraiser> appraisers;
        if (status == null) {
            appraisers = appraiserRepository.findAll(pageable);
        } else {
            appraisers = appraiserRepository.findByStatus(status, pageable);
        }
        return appraisers.map(this::buildAdminProfileResponse);
    }
    @Transactional(readOnly = true)
    public Page<AppraiserProfileResponse> getPendingAppraisers(Pageable pageable) {
        log.debug("Fetching pending appraisers");
        Page<Appraiser> appraisers = appraiserRepository.findByStatus(
                AppraiserStatus.PENDING, pageable);
        return appraisers.map(this::buildAdminProfileResponse);
    }
    @Transactional(readOnly = true)
    public AppraiserStatusResponse getStatus(UUID appraiserId) {
        Appraiser appraiser = appraiserHelper.getAppraiser(appraiserId);
        return appraiserMapper.toStatusResponse(appraiser);
    }
    public AppraiserStatusResponse approveAppraiser(UUID appraiserId) {
        log.info("Approving appraiser: {}", appraiserId);
        Appraiser appraiser = appraiserHelper.getAppraiser(appraiserId);
        try {
            appraiser.approve();
        } catch (IllegalStateException e) {
            throw new AppException(
                    ErrorCode.APPRAISER_STATUS_INVALID_FOR_UPDATE, e.getMessage());
        }
        appraiser = appraiserRepository.save(appraiser);
        log.info("Appraiser approved: {}", appraiserId);
        return appraiserMapper.toStatusResponse(appraiser);
    }
    public AppraiserStatusResponse rejectAppraiser(
            UUID appraiserId, RejectAppraiserRequest request) {
        log.info("Rejecting appraiser: {}", appraiserId);
        Appraiser appraiser = appraiserHelper.getAppraiser(appraiserId);
        try {
            appraiser.reject(request.getRejectionReason());
        } catch (IllegalStateException e) {
            throw new AppException(
                    ErrorCode.APPRAISER_STATUS_INVALID_FOR_UPDATE, e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new AppException(
                    ErrorCode.INVALID_REQUEST, e.getMessage());}
        appraiser = appraiserRepository.save(appraiser);
        log.info("Appraiser rejected: {}", appraiserId);
        return appraiserMapper.toStatusResponse(appraiser);
    }
    public AppraiserStatusResponse suspendAppraiser(
            UUID appraiserId, SuspendAppraiserRequest request) {
        log.info("Suspending appraiser: {}", appraiserId);
        Appraiser appraiser = appraiserHelper.getAppraiser(appraiserId);
        try {
            appraiser.suspend(request.getReason(), request.getSuspensionUntil());
        } catch (IllegalStateException e) {
            throw new AppException(
                    ErrorCode.APPRAISER_STATUS_INVALID_FOR_UPDATE, e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.INVALID_REQUEST, e.getMessage());
        }
        appraiser = appraiserRepository.save(appraiser);
        log.info("Appraiser suspended: {}", appraiserId);
        return appraiserMapper.toStatusResponse(appraiser);
    }
    public AppraiserStatusResponse resumeAppraiser(UUID appraiserId) {
        log.info("Resuming appraiser: {}", appraiserId);
        Appraiser appraiser = appraiserHelper.getAppraiser(appraiserId);
        try {
            appraiser.resume();
        } catch (IllegalStateException e) {
            throw new AppException(
                    ErrorCode.APPRAISER_STATUS_INVALID_FOR_UPDATE,e.getMessage());
        }
        appraiser = appraiserRepository.save(appraiser);
        log.info("Appraiser resumed: {}", appraiserId);
        return appraiserMapper.toStatusResponse(appraiser);
    }
    public AppraiserStatusResponse deactivate(UUID userId) {
        log.info("Deactivating appraiser for user: {}", userId);
        Appraiser appraiser = appraiserHelper.getAppraiserByUserId(userId);
        try {
            appraiser.deactivate();
        } catch (IllegalStateException e) {
            throw new AppException(
                    ErrorCode.APPRAISER_STATUS_INVALID_FOR_UPDATE, e.getMessage());
        }
        appraiser = appraiserRepository.save(appraiser);
        log.info("Appraiser deactivated: {}", userId);
        return appraiserMapper.toStatusResponse(appraiser);
    }
    public AppraiserStatusResponse reactivate(UUID userId) {
        log.info("Reactivating appraiser for user: {}", userId);
        Appraiser appraiser = appraiserHelper.getAppraiserByUserId(userId);
        try {
            appraiser.reactivate();
        } catch (IllegalStateException e) {
            throw new AppException(
                    ErrorCode.APPRAISER_STATUS_INVALID_FOR_UPDATE, e.getMessage());
        }
        appraiser = appraiserRepository.save(appraiser);
        log.info("Appraiser reactivated: {}", userId);
        return appraiserMapper.toStatusResponse(appraiser);
    }
    private AppraiserProfileResponse buildProfileResponse(
            Appraiser appraiser, User user) {
        List<AppraiserCategory> categories =
                appraiserCategoryRepository.findByAppraiserId(appraiser.getId());
        Map<UUID, Category> categoryMap =
                loadCategories(getCategoryIds(categories));
        return appraiserMapper.toProfileResponse(appraiser, user, categories, categoryMap);
    }
    private AppraiserProfileResponse buildAdminProfileResponse(Appraiser appraiser) {
        User user = appraiserHelper.getUser(appraiser.getUserId());
        return buildProfileResponse(appraiser, user);
    }
    private AppraiserPublicProfileResponse buildPublicProfileResponse(Appraiser appraiser) {
        User user = appraiserHelper.getUser(appraiser.getUserId());
        List<AppraiserCategory> categories =
                appraiserCategoryRepository.findByAppraiserId(appraiser.getId());
        Map<UUID, Category> categoryMap =
                loadCategories(getCategoryIds(categories));
        return appraiserMapper.toPublicProfileResponse(appraiser, user, categories, categoryMap);
    }
    private void updateCategories(UUID appraiserId, List<UUID> newCategoryIds) {
        validateCategories(newCategoryIds);
        List<AppraiserCategory> currentCategories =
                appraiserCategoryRepository.findByAppraiserId(appraiserId);
        Set<UUID> newIds = new HashSet<>(newCategoryIds);
        Set<UUID> currentIds = new HashSet<>();
        for (AppraiserCategory category : currentCategories) {
            UUID categoryId = category.getCategoryId();
            currentIds.add(categoryId);
            if (!newIds.contains(categoryId)) {
                appraiserCategoryRepository.deleteByAppraiserIdAndCategoryId(
                        appraiserId, categoryId);}
        }
        List<UUID> categoriesToAdd = new ArrayList<>();
        for (UUID categoryId : newIds) {
            if (!currentIds.contains(categoryId)) {
                categoriesToAdd.add(categoryId);
            }
        }
        appraiserHelper.saveCategories(appraiserId, categoriesToAdd);
    }
    private void validateCategories(List<UUID> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        Set<UUID> uniqueIds = new HashSet<>(categoryIds);
        if (uniqueIds.size() != categoryIds.size()) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        List<Category> foundCategories =
                categoryRepository.findAllById(categoryIds);
        if (foundCategories.size() != categoryIds.size()) {
            throw new AppException(ErrorCode.APPRAISER_CATEGORY_NOT_FOUND);
        }
    }
    private void validateCategoryExists(UUID categoryId) {
        if (!categoryRepository.existsById(categoryId)) {
            throw new AppException(ErrorCode.APPRAISER_CATEGORY_NOT_FOUND);
        }
    }
    private List<UUID> getCategoryIds(
            List<AppraiserCategory> categories) {
        List<UUID> categoryIds = new ArrayList<>();
        for (AppraiserCategory category : categories) {
            categoryIds.add(category.getCategoryId());
        }
        return categoryIds;
    }
    private List<UUID> getAppraiserIdsByCategory(UUID categoryId) {
        List<AppraiserCategory> categories =
                appraiserCategoryRepository.findByCategoryId(categoryId);
        List<UUID> appraiserIds = new ArrayList<>();
        for (AppraiserCategory category : categories) {
            appraiserIds.add(category.getAppraiserId());
        }
        return appraiserIds;
    }
    private Map<UUID, Category> loadCategories(List<UUID> categoryIds) {
        Map<UUID, Category> categoryMap = new HashMap<>();
        if (categoryIds == null || categoryIds.isEmpty()) {
            return categoryMap;
        }
        List<Category> categories =
                categoryRepository.findAllById(categoryIds);
        for (Category category : categories) {
            categoryMap.put(category.getId(), category);
        }
        return categoryMap;
    }
}
