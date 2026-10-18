package com.moldy.moldymarket.pricing.service;

import com.moldy.moldymarket.category.entity.Category;
import com.moldy.moldymarket.category.repository.CategoryRepository;
import com.moldy.moldymarket.pricing.dto.AutoPricingRequest;
import com.moldy.moldymarket.pricing.dto.AutoPricingResponse;
import com.moldy.moldymarket.pricing.entity.*;
import com.moldy.moldymarket.pricing.repository.PricingRequestRepository;
import com.moldy.moldymarket.pricing.repository.ReferencePriceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Auto Pricing Service - Tính giá tự động cho điện tử phổ biến.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AutoPricingService {
    private final ReferencePriceRepository referencePriceRepository;
    private final PricingRequestRepository pricingRequestRepository;
    private final CategoryRepository categoryRepository;

    private static final String SUPPORTED_ORIGIN = "OFFICIAL_VN";
    private static final String DEFAULT_DISCLAIMER = "Giá tham khảo — không phải giá thẩm định chính thức";
    
    // Các category hỗ trợ Auto Pricing (Điện thoại, Laptop)
    private static final String PHONE_CATEGORY_NAME = "Điện thoại";
    private static final String LAPTOP_CATEGORY_NAME = "Laptop";

    /**
     * Tính giá Auto Pricing cho listing.
     * 
     * @param sellerId Người bán (dùng để ghi nhận trong database)
     * @param request Request chứa các thông tin input
     * @return AutoPricingResponse với kết quả hoặc MANUAL_REQUIRED
     */
    @Transactional
    public AutoPricingResponse calculatePrice(UUID sellerId, AutoPricingRequest request) {
        // Kiểm tra các điều kiện áp dụng Auto Pricing
        AutoPricingValidationResult validationResult = validateAutoPricingEligibility(request);
        
        if (!validationResult.isValid()) {
            // Tạo pricing request với status MANUAL_REQUIRED
            PricingRequestEntity pricingRequest = new PricingRequestEntity(
                sellerId,
                request.categoryId(),
                request.modelId(),
                request.origin(),
                request.warrantyStatus(),
                request.conditionLevel(),
                request.usageMonths()
            );
            pricingRequest.setManualRequired(validationResult.getReason());
            pricingRequestRepository.save(pricingRequest);

            return new AutoPricingResponse(
                PricingMode.AUTO,
                PricingStatus.MANUAL_REQUIRED,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                validationResult.getReason()
            );
        }

        // Lấy reference price
        ReferencePriceEntity referencePrice = referencePriceRepository.findById(request.modelId())
            .orElseThrow(() -> new IllegalArgumentException("Model not found: " + request.modelId()));

        // Tính các factor
        BigDecimal deprecationFactor = DepreciationFactorService.calculateFactor(request.usageMonths());
        BigDecimal conditionFactor = request.conditionLevel().getFactor();
        BigDecimal warrantyFactor = request.warrantyStatus().getFactor();

        // Tính giá đề xuất
        long suggestedPrice = PricingCalculationService.calculateSuggestedPrice(
            referencePrice.getReferencePrice(),
            deprecationFactor,
            conditionFactor,
            warrantyFactor
        );

        // Lưu vào database
        PricingRequestEntity pricingRequest = new PricingRequestEntity(
            sellerId,
            request.categoryId(),
            request.modelId(),
            request.origin(),
            request.warrantyStatus(),
            request.conditionLevel(),
            request.usageMonths()
        );
        
        pricingRequest.setAutoPricingResult(
            BigDecimal.valueOf(suggestedPrice),
            referencePrice.getReferencePrice(),
            deprecationFactor,
            conditionFactor,
            warrantyFactor,
            referencePrice.getModelName(),
            referencePrice.getUpdatedAt(),
            DEFAULT_DISCLAIMER
        );
        
        pricingRequestRepository.save(pricingRequest);

        // Trả về response
        return new AutoPricingResponse(
            PricingMode.AUTO,
            PricingStatus.SUGGESTED,
            suggestedPrice,
            referencePrice.getReferencePrice().longValue(),
            deprecationFactor,
            conditionFactor,
            warrantyFactor,
            referencePrice.getModelName(),
            referencePrice.getUpdatedAt(),
            DEFAULT_DISCLAIMER,
            null
        );
    }

    /**
     * Kiểm tra điều kiện áp dụng Auto Pricing.
     */
    private AutoPricingValidationResult validateAutoPricingEligibility(AutoPricingRequest request) {
        // 1. Kiểm tra category có hỗ trợ không
        if (!request.origin().equals(SUPPORTED_ORIGIN)) {
            return new AutoPricingValidationResult(false, "ORIGIN_NOT_SUPPORTED");
        }

        // 2. Kiểm tra category có trong danh sách hỗ trợ không
        if (!isSupportedCategory(request.categoryId())) {
            return new AutoPricingValidationResult(false, "CATEGORY_NOT_SUPPORTED");
        }

        // 3. Kiểm tra model có tồn tại không
        if (!referencePriceRepository.existsById(request.modelId())) {
            return new AutoPricingValidationResult(false, "MODEL_NOT_FOUND");
        }

        return new AutoPricingValidationResult(true, null);
    }

    /**
     * Kiểm tra category có hỗ trợ Auto Pricing hay không.
     * Hiện tại chỉ hỗ trợ Điện thoại (Phone) và Laptop.
     */
    private boolean isSupportedCategory(UUID categoryId) {
        Category category = categoryRepository.findById(categoryId).orElse(null);
        
        if (category == null) {
            return false;
        }
        
        // Kiểm tra tên category
        String categoryName = category.getName();
        return PHONE_CATEGORY_NAME.equals(categoryName) || LAPTOP_CATEGORY_NAME.equals(categoryName);
    }

    /**
     * Helper class để chứa kết quả validation.
     */
    private static class AutoPricingValidationResult {
        private final boolean valid;
        private final String reason;

        AutoPricingValidationResult(boolean valid, String reason) {
            this.valid = valid;
            this.reason = reason;
        }

        boolean isValid() {
            return valid;
        }

        String getReason() {
            return reason;
        }
    }
}
