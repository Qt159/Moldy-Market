package com.moldy.moldymarket.pricing.appraiser.service;

import com.moldy.moldymarket.category.repository.CategoryRepository;
import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.pricing.appraiser.dto.AppraiserPricingCreateRequest;
import com.moldy.moldymarket.pricing.appraiser.dto.AppraiserPricingResponse;
import com.moldy.moldymarket.pricing.appraiser.dto.AppraiserPricingSubmitRequest;
import com.moldy.moldymarket.pricing.appraiser.entity.AppraiserCategoryEntity;
import com.moldy.moldymarket.pricing.appraiser.entity.AppraiserProfileEntity;
import com.moldy.moldymarket.pricing.appraiser.entity.AppraiserStatus;
import com.moldy.moldymarket.pricing.appraiser.mapper.AppraiserMapper;
import com.moldy.moldymarket.pricing.appraiser.repository.AppraiserCategoryRepository;
import com.moldy.moldymarket.pricing.appraiser.repository.AppraiserProfileRepository;
import com.moldy.moldymarket.pricing.entity.PricingMode;
import com.moldy.moldymarket.pricing.entity.PricingRequestEntity;
import com.moldy.moldymarket.pricing.entity.PricingStatus;
import com.moldy.moldymarket.pricing.repository.PricingRequestRepository;
import com.moldy.moldymarket.user.entity.User;
import com.moldy.moldymarket.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AppraiserPricingService {

    private static final String DISCLAIMER_SUFFIX = " Đây là ý kiến về mức giá, không phải xác nhận về tính chính hãng hoặc chất lượng của sản phẩm.";
    private static final String NEW_APPRAISER_LABEL = "Người định giá mới — chưa đủ dữ liệu đánh giá";

    private final PricingRequestRepository pricingRequestRepository;
    private final AppraiserProfileRepository appraiserProfileRepository;
    private final AppraiserCategoryRepository appraiserCategoryRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final AppraiserMapper appraiserMapper;

    @Transactional
    public AppraiserPricingResponse createRequest(UUID sellerId, AppraiserPricingCreateRequest request) {
        if (!categoryRepository.existsById(request.categoryId())) {
            throw new AppException(ErrorCode.CATEGORY_NOT_FOUND);
        }

        if (request.listingId() != null) {
            if (pricingRequestRepository.existsByListingIdAndStatus(request.listingId(), PricingStatus.PENDING)) {
                throw new AppException(ErrorCode.PRICING_REQUEST_ALREADY_PENDING);
            }
            long rejectedCount = pricingRequestRepository.countByListingIdAndStatus(request.listingId(), PricingStatus.SELLER_REJECTED);
            if (rejectedCount >= 3) {
                throw new AppException(ErrorCode.PRICING_REJECT_LIMIT_EXCEEDED);
            }
        }

        UUID targetAppraiserUserId = null;
        String appraiserName = null;

        if (request.appraiserId() != null) {
            AppraiserProfileEntity profile = appraiserProfileRepository.findById(request.appraiserId())
                    .or(() -> appraiserProfileRepository.findByUserId(request.appraiserId()))
                    .orElseThrow(() -> new AppException(ErrorCode.APPRAISER_NOT_FOUND_OR_INACTIVE));

            if (!profile.isApproved()) {
                throw new AppException(ErrorCode.APPRAISER_NOT_FOUND_OR_INACTIVE);
            }

            if (profile.getUserId().equals(sellerId)) {
                throw new AppException(ErrorCode.APPRAISER_CANNOT_APPRAISE_OWN_PRODUCT);
            }

            if (!appraiserCategoryRepository.existsByAppraiserIdAndCategoryId(profile.getId(), request.categoryId())) {
                throw new AppException(ErrorCode.APPRAISER_CATEGORY_NOT_MATCHED);
            }

            targetAppraiserUserId = profile.getUserId();
            User appraiserUser = userRepository.findById(targetAppraiserUserId).orElse(null);
            appraiserName = appraiserUser != null ? appraiserUser.getFullName() : null;
        }

        PricingRequestEntity pricingRequest = new PricingRequestEntity(
                sellerId,
                request.listingId(),
                request.categoryId(),
                targetAppraiserUserId,
                request.commissionRate()
        );

        PricingRequestEntity saved = pricingRequestRepository.save(pricingRequest);
        return appraiserMapper.toPricingResponse(saved, appraiserName);
    }

    public List<AppraiserPricingResponse> getAppraiserQueue(UUID appraiserUserId) {
        AppraiserProfileEntity profile = appraiserProfileRepository.findByUserId(appraiserUserId)
                .orElseThrow(() -> new AppException(ErrorCode.APPRAISER_NOT_FOUND_OR_INACTIVE));

        if (!profile.isApproved()) {
            throw new AppException(ErrorCode.APPRAISER_NOT_FOUND_OR_INACTIVE);
        }

        List<UUID> categoryIds = appraiserCategoryRepository.findByAppraiserId(profile.getId())
                .stream()
                .map(AppraiserCategoryEntity::getCategoryId)
                .toList();

        List<PricingRequestEntity> tasks;
        if (categoryIds.isEmpty()) {
            tasks = pricingRequestRepository.findByAppraiserIdAndStatus(appraiserUserId, PricingStatus.PENDING);
        } else {
            tasks = pricingRequestRepository.findAppraiserQueue(categoryIds, appraiserUserId, PricingStatus.PENDING, PricingMode.APPRAISER);
        }

        return tasks.stream().map(task -> {
            String name = null;
            if (task.getAppraiserId() != null) {
                User u = userRepository.findById(task.getAppraiserId()).orElse(null);
                name = u != null ? u.getFullName() : null;
            }
            return appraiserMapper.toPricingResponse(task, name);
        }).toList();
    }

    @Transactional
    public AppraiserPricingResponse claimTask(UUID appraiserUserId, UUID requestId) {
        PricingRequestEntity task = pricingRequestRepository.findById(requestId)
                .orElseThrow(() -> new AppException(ErrorCode.PRICING_REQUEST_NOT_FOUND));

        if (task.getStatus() != PricingStatus.PENDING) {
            throw new AppException(ErrorCode.INVALID_PRICING_STATUS);
        }

        if (task.getAppraiserId() != null && !task.getAppraiserId().equals(appraiserUserId)) {
            throw new AppException(ErrorCode.PRICING_REQUEST_ALREADY_CLAIMED);
        }

        AppraiserProfileEntity profile = appraiserProfileRepository.findByUserId(appraiserUserId)
                .orElseThrow(() -> new AppException(ErrorCode.APPRAISER_NOT_FOUND_OR_INACTIVE));

        if (!profile.isApproved()) {
            throw new AppException(ErrorCode.APPRAISER_NOT_FOUND_OR_INACTIVE);
        }

        if (task.getSellerId().equals(appraiserUserId)) {
            throw new AppException(ErrorCode.APPRAISER_CANNOT_APPRAISE_OWN_PRODUCT);
        }

        if (task.getCategoryId() != null &&
            !appraiserCategoryRepository.existsByAppraiserIdAndCategoryId(profile.getId(), task.getCategoryId())) {
            throw new AppException(ErrorCode.APPRAISER_CATEGORY_NOT_MATCHED);
        }

        task.assignAppraiser(appraiserUserId);
        PricingRequestEntity saved = pricingRequestRepository.save(task);

        User appraiserUser = userRepository.findById(appraiserUserId).orElse(null);
        String name = appraiserUser != null ? appraiserUser.getFullName() : null;

        return appraiserMapper.toPricingResponse(saved, name);
    }

    @Transactional
    public AppraiserPricingResponse submitAppraisal(UUID appraiserUserId, UUID requestId, AppraiserPricingSubmitRequest submitRequest) {
        PricingRequestEntity task = pricingRequestRepository.findById(requestId)
                .orElseThrow(() -> new AppException(ErrorCode.PRICING_REQUEST_NOT_FOUND));

        if (task.getStatus() != PricingStatus.PENDING) {
            throw new AppException(ErrorCode.INVALID_PRICING_STATUS);
        }

        if (task.getAppraiserId() == null || !task.getAppraiserId().equals(appraiserUserId)) {
            throw new AppException(ErrorCode.APPRAISER_NOT_ASSIGNED);
        }

        if (task.getSellerId().equals(appraiserUserId)) {
            throw new AppException(ErrorCode.APPRAISER_CANNOT_APPRAISE_OWN_PRODUCT);
        }

        AppraiserProfileEntity profile = appraiserProfileRepository.findByUserId(appraiserUserId).orElse(null);
        User user = userRepository.findById(appraiserUserId).orElse(null);

        String appraiserName = user != null ? user.getFullName() : "Người định giá";
        String reputation;
        if (profile != null && profile.getTotalCompleted() != null && profile.getTotalCompleted() >= 10 && profile.getAccuracyPercentage() != null) {
            reputation = String.format(Locale.US, "%.1f%%", profile.getAccuracyPercentage());
        } else {
            reputation = NEW_APPRAISER_LABEL;
        }

        String disclaimer = "Đã tham khảo giá bởi " + appraiserName + " - [" + reputation + "]" + DISCLAIMER_SUFFIX;

        task.submitAppraiserPricing(submitRequest.suggestedPrice(), submitRequest.note(), disclaimer);
        PricingRequestEntity saved = pricingRequestRepository.save(task);

        return appraiserMapper.toPricingResponse(saved, appraiserName);
    }

    @Transactional
    public AppraiserPricingResponse acceptAppraisal(UUID sellerId, UUID requestId) {
        PricingRequestEntity task = pricingRequestRepository.findById(requestId)
                .orElseThrow(() -> new AppException(ErrorCode.PRICING_REQUEST_NOT_FOUND));

        if (!task.getSellerId().equals(sellerId)) {
            throw new AppException(ErrorCode.NOT_REQUEST_SELLER);
        }

        if (task.getStatus() != PricingStatus.SUGGESTED) {
            throw new AppException(ErrorCode.INVALID_PRICING_STATUS);
        }

        task.acceptBySeller();
        PricingRequestEntity saved = pricingRequestRepository.save(task);

        String appraiserName = null;
        if (saved.getAppraiserId() != null) {
            User user = userRepository.findById(saved.getAppraiserId()).orElse(null);
            appraiserName = user != null ? user.getFullName() : null;
        }

        return appraiserMapper.toPricingResponse(saved, appraiserName);
    }

    @Transactional
    public AppraiserPricingResponse rejectAppraisal(UUID sellerId, UUID requestId) {
        PricingRequestEntity task = pricingRequestRepository.findById(requestId)
                .orElseThrow(() -> new AppException(ErrorCode.PRICING_REQUEST_NOT_FOUND));

        if (!task.getSellerId().equals(sellerId)) {
            throw new AppException(ErrorCode.NOT_REQUEST_SELLER);
        }

        if (task.getStatus() != PricingStatus.SUGGESTED) {
            throw new AppException(ErrorCode.INVALID_PRICING_STATUS);
        }

        task.rejectBySeller();
        PricingRequestEntity saved = pricingRequestRepository.save(task);

        String appraiserName = null;
        if (saved.getAppraiserId() != null) {
            User user = userRepository.findById(saved.getAppraiserId()).orElse(null);
            appraiserName = user != null ? user.getFullName() : null;
        }

        return appraiserMapper.toPricingResponse(saved, appraiserName);
    }

    public List<AppraiserPricingResponse> getPricingByListingId(UUID listingId) {
        List<PricingRequestEntity> requests = pricingRequestRepository.findByListingId(listingId);
        return requests.stream().map(req -> {
            String appraiserName = null;
            if (req.getAppraiserId() != null) {
                User user = userRepository.findById(req.getAppraiserId()).orElse(null);
                appraiserName = user != null ? user.getFullName() : null;
            }
            return appraiserMapper.toPricingResponse(req, appraiserName);
        }).toList();
    }

    public AppraiserPricingResponse getPricingRequest(UUID requestId) {
        PricingRequestEntity task = pricingRequestRepository.findById(requestId)
                .orElseThrow(() -> new AppException(ErrorCode.PRICING_REQUEST_NOT_FOUND));

        String appraiserName = null;
        if (task.getAppraiserId() != null) {
            User user = userRepository.findById(task.getAppraiserId()).orElse(null);
            appraiserName = user != null ? user.getFullName() : null;
        }

        return appraiserMapper.toPricingResponse(task, appraiserName);
    }
}
