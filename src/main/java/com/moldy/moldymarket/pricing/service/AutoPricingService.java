package com.moldy.moldymarket.pricing.service;

import com.moldy.moldymarket.category.entity.Category;
import com.moldy.moldymarket.category.repository.CategoryRepository;
import com.moldy.moldymarket.pricing.dto.AutoPricingRequest;
import com.moldy.moldymarket.pricing.dto.AutoPricingResponse;
import com.moldy.moldymarket.pricing.entity.*;
import com.moldy.moldymarket.pricing.helper.DepreciationFactorHelper;
import com.moldy.moldymarket.pricing.helper.PricingCalculationHelper;
import com.moldy.moldymarket.pricing.mapper.AutoPricingMapper;
import com.moldy.moldymarket.pricing.repository.PricingRequestRepository;
import com.moldy.moldymarket.pricing.repository.ReferencePriceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AutoPricingService {
    private final ReferencePriceRepository referencePriceRepository;
    private final PricingRequestRepository pricingRequestRepository;
    private final CategoryRepository categoryRepository;
    private final AutoPricingMapper autoPricingMapper;

    private static final String SUPPORTED_ORIGIN = "OFFICIAL_VN";
    private static final String DEFAULT_DISCLAIMER = "Giá tham khảo — không phải giá thẩm định chính thức";
    
    // Supported categories for Auto Pricing 
    private static final String PHONE_CATEGORY_NAME = "Điện thoại";
    private static final String LAPTOP_CATEGORY_NAME = "Laptop";

    /*
     sellerId Seller ID (for logging)
     request Request with pricing inputs
     return AutoPricingResponse with suggested price or MANUAL_REQUIRED reason
     */
    @Transactional
    public AutoPricingResponse calculatePrice(UUID sellerId, AutoPricingRequest request) {
        // Validate 
        AutoPricingValidationResult validationResult = validateAutoPricingEligibility(request);
        
        if (!validationResult.isValid()) {
            // Save as MANUAL_REQUIRED
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

            return autoPricingMapper.toManualRequiredResponse(validationResult.getReason());
        }

        // reference price
        ReferencePriceEntity referencePrice = referencePriceRepository.findById(request.modelId())
            .orElseThrow(() -> new IllegalArgumentException("Model not found: " + request.modelId()));

    
        BigDecimal deprecationFactor = DepreciationFactorHelper.calculateFactor(request.usageMonths());
        BigDecimal conditionFactor = request.conditionLevel().getFactor();
        BigDecimal warrantyFactor = request.warrantyStatus().getFactor();

    
        long suggestedPrice = PricingCalculationHelper.calculateSuggestedPrice(
            referencePrice.getReferencePrice(),
            deprecationFactor,
            conditionFactor,
            warrantyFactor
        );

        
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

        return autoPricingMapper.toSuggestedResponse(
            suggestedPrice,
            referencePrice.getReferencePrice(),
            deprecationFactor,
            conditionFactor,
            warrantyFactor,
            referencePrice.getModelName(),
            referencePrice.getUpdatedAt(),
            DEFAULT_DISCLAIMER
        );
    }


    private AutoPricingValidationResult validateAutoPricingEligibility(AutoPricingRequest request) {
    
        if (!request.origin().equals(SUPPORTED_ORIGIN)) {
            return new AutoPricingValidationResult(false, "ORIGIN_NOT_SUPPORTED");
        }

        
        if (!isSupportedCategory(request.categoryId())) {
            return new AutoPricingValidationResult(false, "CATEGORY_NOT_SUPPORTED");
        }

        if (!referencePriceRepository.existsById(request.modelId())) {
            return new AutoPricingValidationResult(false, "MODEL_NOT_FOUND");
        }

        return new AutoPricingValidationResult(true, null);
    }

    /
    private boolean isSupportedCategory(UUID categoryId) {
        return categoryRepository.findById(categoryId)
            .map(category -> PHONE_CATEGORY_NAME.equals(category.getName()) 
                         || LAPTOP_CATEGORY_NAME.equals(category.getName()))
            .orElse(false);

        return new AutoPricingValidationResult(true, null);
    }
    //check supported
    private boolean isSupportedCategory(UUID categoryId) {
        Category category = categoryRepository.findById(categoryId).orElse(null);
        
        if (category == null) {
            return false;
        }
        
        String categoryName = category.getName();
        return PHONE_CATEGORY_NAME.equals(categoryName) || LAPTOP_CATEGORY_NAME.equals(categoryName);
    }

    
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
